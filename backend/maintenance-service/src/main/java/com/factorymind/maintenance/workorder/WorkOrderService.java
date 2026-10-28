package com.factorymind.maintenance.workorder;

import com.factorymind.maintenance.alert.AlertEventMessage;
import com.factorymind.maintenance.common.ConflictException;
import com.factorymind.maintenance.common.NotFoundException;
import com.factorymind.maintenance.common.PageResponse;
import com.factorymind.maintenance.inventory.InventoryClient;
import com.factorymind.maintenance.risk.RiskLevelChangedMessage;
import com.factorymind.maintenance.inventory.IssuedStock;
import com.factorymind.maintenance.workorder.WorkOrderDtos.CreateWorkOrderRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.MaintenanceSummary;
import com.factorymind.maintenance.workorder.WorkOrderDtos.UpdateWorkOrderRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.UsePartRequest;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderResponse;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderSummary;
import com.factorymind.maintenance.workorder.WorkOrderSpecifications.WorkOrderFilter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkOrderService {

    static final String ALERT_SERVICE = "alert-service";
    static final String AI_INSIGHTS = "ai-insights";
    private static final List<WorkOrderStatus> OPEN_STATUSES =
            List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private static final Logger log = LoggerFactory.getLogger(WorkOrderService.class);

    private final WorkOrderRepository workOrders;
    private final InventoryClient inventory;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkOrderService(WorkOrderRepository workOrders, InventoryClient inventory,
                            ApplicationEventPublisher events, Clock clock) {
        this.workOrders = workOrders;
        this.inventory = inventory;
        this.events = events;
        this.clock = clock;
    }

    public PageResponse<WorkOrderSummary> search(WorkOrderFilter filter, Pageable pageable) {
        LocalDate today = today();
        return PageResponse.of(workOrders.findAll(WorkOrderSpecifications.matching(filter), pageable),
                w -> WorkOrderSummary.of(w, today));
    }

    public WorkOrderResponse findById(UUID id) {
        return WorkOrderResponse.of(get(id), today());
    }

    @Transactional
    public WorkOrderResponse create(CreateWorkOrderRequest request, String user) {
        if (request.alertId() != null && workOrders.existsForAlert(request.alertId())) {
            throw new ConflictException("A work order already exists for this alert");
        }
        WorkOrder workOrder = workOrders.save(new WorkOrder(nextCode(), request.assetId(), request.alertId(),
                request.type(), request.priority(), request.title().strip(), request.description(),
                request.assignee(), request.dueDate(), user, clock.instant()));
        announce(ActivityType.CREATED, workOrder, user);
        return WorkOrderResponse.of(workOrder, today());
    }

    @Transactional
    public WorkOrderResponse update(UUID id, UpdateWorkOrderRequest request, String user) {
        WorkOrder workOrder = get(id);
        workOrder.update(request.title().strip(), request.description(), request.priority(), request.assignee(),
                request.dueDate(), user, clock.instant());
        announce(ActivityType.UPDATED, workOrder, user);
        return WorkOrderResponse.of(workOrder, today());
    }

    @Transactional
    public WorkOrderResponse changeStatus(UUID id, WorkOrderStatus status, String note, String user) {
        WorkOrder workOrder = get(id);
        workOrder.moveTo(status, note, user, clock.instant());
        announce(ActivityType.STATUS_CHANGED, workOrder, user);
        return WorkOrderResponse.of(workOrder, today());
    }

    /**
     * Takes the part out of stock in inventory-service, then books it on the work order. If booking fails
     * after the stock movement (for example someone else changed the work order meanwhile), the part is
     * put back, so stock and work orders never disagree for long.
     */
    @Transactional
    public WorkOrderResponse usePart(UUID id, UsePartRequest request, String user) {
        WorkOrder workOrder = get(id);
        workOrder.ensureCanUseParts();
        UUID lineId = UUID.randomUUID();
        IssuedStock issued = inventory.issue(request.partId(), request.quantity(), lineId, workOrder.getId(),
                workOrder.getCode());
        try {
            workOrder.addPart(new WorkOrderPart(lineId, issued.partId(), issued.partNumber(), issued.partName(),
                    issued.unit(), issued.quantity(), issued.unitCost(), user, clock.instant()), user, clock.instant());
            workOrders.saveAndFlush(workOrder);
        } catch (RuntimeException e) {
            compensate(request, lineId, workOrder, e);
            throw e;
        }
        announce(ActivityType.PART_USED, workOrder, user);
        return WorkOrderResponse.of(workOrder, today());
    }

    /** Puts a booked part back into stock. Safe to retry: the return has a request id derived from the line. */
    @Transactional
    public WorkOrderResponse returnPart(UUID id, UUID lineId, String user) {
        WorkOrder workOrder = get(id);
        WorkOrderPart part = workOrder.part(lineId);
        workOrder.ensureCanUseParts();
        inventory.giveBack(part.getPartId(), part.getQuantity(), returnRequestId(lineId), workOrder.getId(),
                workOrder.getCode());
        workOrder.removePart(part, user, clock.instant());
        announce(ActivityType.PART_RETURNED, workOrder, user);
        return WorkOrderResponse.of(workOrder, today());
    }

    /**
     * A CRITICAL alert gets an urgent corrective work order without waiting for someone to open one.
     * A machine that is failing trips several sensors: if an urgent job for its alerts is still open, the
     * new alert joins that job instead of starting another. Kafka can deliver the same event twice and an
     * alert can escalate after it has a work order: an alert never ends up on two work orders.
     */
    @Transactional
    public void openForCriticalAlert(AlertEventMessage alert) {
        if (workOrders.existsForAlert(alert.alertId())) {
            return;
        }
        String sensor = alert.sensorType().replace('_', ' ').toLowerCase(Locale.ROOT);
        String reading = sensor + " reached " + plain(alert.value()) + " " + nullToEmpty(alert.unit())
                + " (critical limit " + plain(alert.criticalThreshold()) + " " + nullToEmpty(alert.unit()) + ")";
        List<WorkOrder> running = workOrders.findOpenForAlertsOn(alert.assetId(), OPEN_STATUSES);
        if (!running.isEmpty()) {
            WorkOrder workOrder = running.getFirst();
            workOrder.linkAlert(alert.alertId(), "Critical " + reading, ALERT_SERVICE, clock.instant());
            log.info("Linked critical alert {} to {}", alert.alertId(), workOrder.getCode());
            announce(ActivityType.ALERT_LINKED, workOrder, ALERT_SERVICE);
            return;
        }
        WorkOrder workOrder = workOrders.save(new WorkOrder(nextCode(), alert.assetId(), alert.alertId(),
                WorkOrderType.CORRECTIVE, WorkOrderPriority.URGENT, "Critical " + sensor + " alarm",
                "Raised automatically: " + reading + ". Inspect the machine and record what was found.",
                null, today(), ALERT_SERVICE, clock.instant()));
        log.info("Opened {} for critical alert {} on machine {}", workOrder.getCode(), alert.alertId(), alert.assetId());
        announce(ActivityType.CREATED, workOrder, ALERT_SERVICE);
    }

    /**
     * The failure-risk model expects a machine to reach a critical limit soon, usually before any alert.
     * An inspection is planned so someone looks at the machine while it still runs. If the machine already
     * has an open work order of any kind, that job covers it and nothing is added; this also makes a
     * redelivered event harmless, since it finds the inspection it opened the first time.
     */
    @Transactional
    public void planInspectionForRisk(RiskLevelChangedMessage risk) {
        if (workOrders.existsByAssetIdAndStatusIn(risk.assetId(), OPEN_STATUSES)) {
            log.info("Machine {} rated HIGH risk; an open work order already covers it", risk.assetId());
            return;
        }
        String sensor = risk.mainSensorType().replace('_', ' ').toLowerCase(Locale.ROOT);
        String description = "Planned automatically by the failure-risk model (" + risk.modelVersion() + "): "
                + Math.round(risk.riskScore() * 100) + "% probability that the " + sensor
                + " reaches its critical limit within 24 h. " + String.join("; ", risk.drivers())
                + ". Inspect the machine before it fails and record what was found.";
        WorkOrder workOrder = workOrders.save(new WorkOrder(nextCode(), risk.assetId(), null,
                WorkOrderType.INSPECTION, WorkOrderPriority.HIGH, "Predicted " + sensor + " failure",
                description.length() > 2000 ? description.substring(0, 2000) : description,
                null, today(), AI_INSIGHTS, clock.instant()));
        log.info("Planned {} for machine {} (risk {})", workOrder.getCode(), risk.assetId(), risk.riskScore());
        announce(ActivityType.CREATED, workOrder, AI_INSIGHTS);
    }

    public MaintenanceSummary summary() {
        Map<WorkOrderStatus, Long> byStatus = new EnumMap<>(WorkOrderStatus.class);
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            byStatus.put(status, 0L);
        }
        workOrders.countByStatus().forEach(row -> byStatus.put((WorkOrderStatus) row.getKey(), row.getTotal()));
        long open = OPEN_STATUSES.stream().mapToLong(byStatus::get).sum();

        Instant since = clock.instant().minus(Duration.ofDays(30));
        List<WorkOrder> completed = workOrders.findByStatusAndCompletedAtAfter(WorkOrderStatus.COMPLETED, since);
        OptionalDouble mttrMinutes = completed.stream()
                .filter(w -> w.getType() == WorkOrderType.CORRECTIVE)
                .mapToLong(w -> Duration.between(w.getCreatedAt(), w.getCompletedAt()).toMinutes())
                .average();
        Double mttrHours = mttrMinutes.isPresent() ? Math.round(mttrMinutes.getAsDouble() / 6.0) / 10.0 : null;

        return new MaintenanceSummary(byStatus, open,
                workOrders.countByStatusInAndDueDateBefore(OPEN_STATUSES, today()),
                workOrders.countByStatusInAndPriority(OPEN_STATUSES, WorkOrderPriority.URGENT),
                mttrHours, completed.size());
    }

    private void compensate(UsePartRequest request, UUID lineId, WorkOrder workOrder, RuntimeException cause) {
        try {
            inventory.giveBack(request.partId(), request.quantity(), returnRequestId(lineId), workOrder.getId(),
                    workOrder.getCode());
        } catch (RuntimeException e) {
            cause.addSuppressed(e);
            log.error("Part {} x{} was issued for {} but could not be booked or returned; fix the stock by hand",
                    request.partId(), request.quantity(), workOrder.getCode(), e);
        }
    }

    private void announce(ActivityType type, WorkOrder workOrder, String actor) {
        events.publishEvent(WorkOrderEventMessage.of(type, workOrder, actor, clock.instant()));
    }

    private String nextCode() {
        return "WO-%05d".formatted(workOrders.nextNumber());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private WorkOrder get(UUID id) {
        return workOrders.findById(id).orElseThrow(() -> new NotFoundException("Work order", id));
    }

    static UUID returnRequestId(UUID lineId) {
        return UUID.nameUUIDFromBytes(("return:" + lineId).getBytes(StandardCharsets.UTF_8));
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
