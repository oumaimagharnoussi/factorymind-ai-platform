package com.factorymind.notification.event;

/** Where an event comes from. A routing rule can listen to one source or to all of them. */
public enum EventSource {
    /** alert-service: a sensor alert was raised, escalated or came back. */
    ALERT,
    /** maintenance-service: a work order was opened or completed. */
    WORK_ORDER,
    /** ai-insights-service: a machine's failure risk turned HIGH. */
    AI_RISK,
    /** Prometheus through Alertmanager: a service is down, slow, lagging... */
    PLATFORM
}
