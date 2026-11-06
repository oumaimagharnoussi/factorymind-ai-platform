package com.factorymind.maintenance.plan;

import com.factorymind.maintenance.common.BusinessRuleException;
import com.factorymind.maintenance.common.ConflictException;
import com.factorymind.maintenance.common.NotFoundException;
import com.factorymind.maintenance.plan.PlanDtos.CurrentJob;
import com.factorymind.maintenance.plan.PlanDtos.PlanRequest;
import com.factorymind.maintenance.plan.PlanDtos.PlanResponse;
import com.factorymind.maintenance.workorder.PlannedJob;
import com.factorymind.maintenance.workorder.WorkOrder;
import com.factorymind.maintenance.workorder.WorkOrderDtos.WorkOrderSummary;
import com.factorymind.maintenance.workorder.WorkOrderRepository;
import com.factorymind.maintenance.workorder.WorkOrderRepository.LastCompletion;
import com.factorymind.maintenance.workorder.WorkOrderService;
import com.factorymind.maintenance.workorder.WorkOrderStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preventive maintenance plans and the work orders they open. See docs/adr/0012. */
@Service
@Transactional(readOnly = true)
public class PlanService {

    static final List<WorkOrderStatus> OPEN_STATUSES =
            List.of(WorkOrderStatus.OPEN, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.ON_HOLD);

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final MaintenancePlanRepository plans;
    private final WorkOrderRepository workOrders;
    private final WorkOrderService workOrderService;
    private final Clock clock;

    public PlanService(MaintenancePlanRepository plans, WorkOrderRepository workOrders,
                       WorkOrderService workOrderService, Clock clock) {
        this.plans = plans;
        this.workOrders = workOrders;
        this.workOrderService = workOrderService;
        this.clock = clock;
    }

    /** Soonest due first; optionally the plans of one machine, or only the active ones. */
    public List<PlanResponse> list(UUID assetId, Boolean active) {
        List<MaintenancePlan> found = plans.findAllByOrderByNextDueDateAscCodeAsc().stream()
                .filter(p -> assetId == null || p.getAssetId().equals(assetId))
                .filter(p -> active == null || p.isActive() == active)
                .toList();
        return describe(found);
    }

    public PlanResponse findById(UUID id) {
        return describe(List.of(get(id))).getFirst();
    }

    @Transactional
    public PlanResponse create(PlanRequest request, String user) {
        ensureNotPast(request.nextDueDate());
        MaintenancePlan plan = plans.save(new MaintenancePlan("PM-%05d".formatted(plans.nextNumber()),
                request.assetId(), request.title(), request.instructions(), request.schedule(), request.intervalDays(),
                request.leadDays(), request.priority(), request.assignee(), request.nextDueDate(), user));
        log.info("{} created plan {} on machine {}: every {} days", user, plan.getCode(), plan.getAssetId(),
                plan.getIntervalDays());
        return findById(plan.getId());
    }

    /** The machine of a plan is fixed; everything else, including the next due date, can be changed. */
    @Transactional
    public PlanResponse update(UUID id, PlanRequest request, String user) {
        MaintenancePlan plan = get(id);
        if (!plan.getAssetId().equals(request.assetId())) {
            throw new BusinessRuleException("A plan stays on its machine; create a new plan for another machine");
        }
        if (!request.nextDueDate().equals(plan.getNextDueDate())) {
            ensureNotPast(request.nextDueDate());
        }
        plan.update(request.title(), request.instructions(), request.schedule(), request.intervalDays(),
                request.leadDays(), request.priority(), request.assignee(), request.nextDueDate());
        plans.flush();
        log.info("{} updated plan {}", user, plan.getCode());
        return describe(List.of(plan)).getFirst();
    }

    @Transactional
    public PlanResponse pause(UUID id, String user) {
        MaintenancePlan plan = get(id);
        plan.pause();
        plans.flush();
        log.info("{} paused plan {}", user, plan.getCode());
        return describe(List.of(plan)).getFirst();
    }

    @Transactional
    public PlanResponse resume(UUID id, String user) {
        MaintenancePlan plan = get(id);
        plan.resume(today());
        plans.flush();
        log.info("{} resumed plan {}", user, plan.getCode());
        return describe(List.of(plan)).getFirst();
    }

    /**
     * Opens the next work order now, without waiting for the lead time: for example while the machine is stopped
     * for another reason. The plan's rule still holds: one open job at a time.
     */
    @Transactional
    public WorkOrderSummary openNow(UUID id, String user) {
        MaintenancePlan plan = get(id);
        if (!plan.isActive()) {
            throw new BusinessRuleException("Plan " + plan.getCode() + " is paused; resume it first");
        }
        if (workOrders.existsByPlanIdAndStatusIn(plan.getId(), OPEN_STATUSES)) {
            throw new ConflictException("Plan " + plan.getCode() + " already has an open work order");
        }
        log.info("{} opened the next work order of plan {} by hand", user, plan.getCode());
        return open(plan);
    }

    /**
     * Called by the planner, one plan per transaction. Re-checks everything on fresh data, so a plan another
     * instance has just handled is skipped; if two instances still race, the plan's version and the database's
     * one-open-job index let only one of them commit.
     *
     * @return the work order opened, or null when there was nothing to do
     */
    @Transactional
    public WorkOrderSummary openIfDue(UUID id) {
        MaintenancePlan plan = plans.findById(id).orElse(null);
        if (plan == null || !plan.isDue(today()) || workOrders.existsByPlanIdAndStatusIn(id, OPEN_STATUSES)) {
            return null;
        }
        return open(plan);
    }

    /** IDs of the plans that should have a work order open by today. */
    public List<UUID> dueIds() {
        return plans.findDueIds(today());
    }

    /** One of the plan's work orders was completed or cancelled. */
    @Transactional
    public void jobClosed(UUID planId, Instant closedAt) {
        plans.findById(planId).ifPresent(plan -> plan.jobClosed(LocalDate.ofInstant(closedAt, clock.getZone())));
    }

    private WorkOrderSummary open(MaintenancePlan plan) {
        String description = (plan.getInstructions() == null ? "" : plan.getInstructions() + "\n\n")
                + "Preventive maintenance plan " + plan.getCode() + ": every " + plan.getIntervalDays() + " days"
                + (plan.getSchedule() == PlanSchedule.FLOATING ? " after the last job." : " on the calendar.");
        WorkOrderSummary workOrder = workOrderService.openForPlan(new PlannedJob(plan.getId(), plan.getCode(),
                plan.getAssetId(), plan.getPriority(), plan.getTitle(),
                description.length() > 2000 ? description.substring(0, 2000) : description, plan.getAssignee(),
                plan.dueDateWhenOpenedOn(today())));
        plan.occurrenceOpened(clock.instant());
        return workOrder;
    }

    private List<PlanResponse> describe(List<MaintenancePlan> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Collection<UUID> ids = found.stream().map(MaintenancePlan::getId).toList();
        Map<UUID, WorkOrder> openJobs = workOrders.findByPlanIdInAndStatusIn(ids, OPEN_STATUSES).stream()
                .collect(Collectors.toMap(WorkOrder::getPlanId, Function.identity(), (a, b) -> a));
        Map<UUID, Instant> lastCompleted = workOrders.lastCompletions(ids).stream()
                .collect(Collectors.toMap(LastCompletion::getPlanId, LastCompletion::getCompletedAt));
        LocalDate today = today();
        return found.stream().map(p -> {
            WorkOrder job = openJobs.get(p.getId());
            CurrentJob current = job == null ? null : new CurrentJob(job.getId(), job.getCode(), job.getStatus(),
                    job.getDueDate(), job.isOverdue(today));
            PlanState state = !p.isActive() ? PlanState.PAUSED
                    : job != null ? PlanState.JOB_OPEN
                    : p.isDue(today) ? PlanState.DUE
                    : PlanState.SCHEDULED;
            return new PlanResponse(p.getId(), p.getCode(), p.getAssetId(), p.getTitle(), p.getInstructions(),
                    p.getSchedule(), p.getIntervalDays(), p.getLeadDays(), p.getPriority(), p.getAssignee(),
                    p.isActive(), state, p.getNextDueDate(), p.opensOn(), current, lastCompleted.get(p.getId()),
                    p.getLastGeneratedAt(), p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }).toList();
    }

    private void ensureNotPast(LocalDate dueDate) {
        if (dueDate.isBefore(today())) {
            throw new BusinessRuleException("The next due date cannot be in the past");
        }
    }

    private MaintenancePlan get(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Maintenance plan", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
