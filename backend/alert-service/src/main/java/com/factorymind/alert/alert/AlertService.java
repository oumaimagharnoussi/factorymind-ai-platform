package com.factorymind.alert.alert;

import com.factorymind.alert.alert.AlertDtos.AlertEvent;
import com.factorymind.alert.alert.AlertDtos.AlertResponse;
import com.factorymind.alert.alert.AlertDtos.AlertSummary;
import com.factorymind.alert.alert.AlertSpecifications.AlertFilter;
import com.factorymind.alert.common.NotFoundException;
import com.factorymind.alert.common.PageResponse;
import com.factorymind.alert.workorder.WorkOrderEventMessage;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What people (and maintenance-service) do with alerts. Raising and clearing is AlarmEngine's job. */
@Service
@Transactional(readOnly = true)
public class AlertService {

    static final String SYSTEM = "system";

    private final AlertRepository alerts;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public AlertService(AlertRepository alerts, ApplicationEventPublisher events, Clock clock) {
        this.alerts = alerts;
        this.events = events;
        this.clock = clock;
    }

    public PageResponse<AlertResponse> search(AlertFilter filter, Pageable pageable) {
        return PageResponse.of(alerts.findAll(AlertSpecifications.matching(filter), pageable), AlertResponse::of);
    }

    public AlertResponse findById(UUID id) {
        return AlertResponse.of(get(id));
    }

    public AlertSummary summary() {
        return new AlertSummary(
                alerts.countBySeverityAndStatusNot(AlertSeverity.CRITICAL, AlertStatus.RESOLVED),
                alerts.countBySeverityAndStatusNot(AlertSeverity.WARNING, AlertStatus.RESOLVED),
                alerts.countByActiveTrueAndStatusNot(AlertStatus.RESOLVED),
                alerts.countByStatus(AlertStatus.OPEN));
    }

    @Transactional
    public AlertResponse acknowledge(UUID id, String user) {
        Alert alert = get(id);
        if (alert.acknowledge(user, clock.instant())) {
            announce(AlertEventType.ACKNOWLEDGED, alert);
        }
        return AlertResponse.of(alert);
    }

    @Transactional
    public AlertResponse resolve(UUID id, String user, String note) {
        Alert alert = get(id);
        alert.resolve(user, note, clock.instant());
        announce(AlertEventType.RESOLVED, alert);
        return AlertResponse.of(alert);
    }

    /**
     * Keeps alerts in step with the work order that answers them: being put on a work order acknowledges
     * an alert, completing the work order resolves it if the sensor is already back to normal (otherwise
     * the clearing does).
     */
    @Transactional
    public void onWorkOrderEvent(WorkOrderEventMessage event) {
        if (event.alertIds() == null) {
            return;
        }
        alerts.findAllById(event.alertIds()).stream().filter(alert -> !alert.isResolved()).forEach(alert -> {
            boolean linked = alert.linkWorkOrder(event.workOrderId(), event.code(), event.status());
            String actor = event.actor() == null ? SYSTEM : event.actor();
            boolean acknowledged = alert.acknowledge(actor, clock.instant());
            if (linked || acknowledged) {
                announce(AlertEventType.WORK_ORDER_UPDATED, alert);
            }
            if (alert.workOrderCompleted() && !alert.isActive()) {
                alert.resolve(actor, "Work order " + event.code() + " completed", clock.instant());
                announce(AlertEventType.RESOLVED, alert);
            }
        });
    }

    private void announce(AlertEventType type, Alert alert) {
        events.publishEvent(new AlertEvent(type, AlertResponse.of(alert)));
    }

    private Alert get(UUID id) {
        return alerts.findById(id).orElseThrow(() -> new NotFoundException("Alert", id));
    }
}
