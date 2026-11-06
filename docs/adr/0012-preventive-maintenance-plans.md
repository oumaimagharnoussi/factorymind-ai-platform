# ADR 0012: Preventive maintenance plans

- Status: accepted
- Date: 2026-11-06

## Context

Until now, work orders came from three places: a critical alert (corrective), the failure-risk model (inspection)
or a person. Routine servicing, such as greasing a motor every month or changing a compressor's oil every 90 days,
had to be remembered and typed in by hand. A maintenance system without it
cannot show the share of planned work, which is one of the first numbers a maintenance manager looks at.

Plans could be driven by the calendar or by running hours. Running hours need a meter per machine. The platform
does not have one: the simulator sends readings all the time, whether the machine runs or not. So this phase
plans by the calendar only.

## Decision

**A maintenance plan lives in maintenance-service and opens ordinary PREVENTIVE work orders.**

- A plan (`PM-00001`) belongs to one machine and holds:
  - the job: title, instructions, priority and usual technician;
  - the interval in days;
  - the lead time: how many days before the due date its work order opens, so the job can be scheduled and the
    parts ordered;
  - the next due date.
- There are two ways to count the interval:
  - **FIXED**: every N days on the calendar, even when the last job was late. This is for inspections required by
    law or by the manufacturer. The next date moves as soon as the work order is opened.
  - **FLOATING**: N days after the last job was closed. This is for wear items such as lubrication and filters.
    The next date is set when the work order is completed or cancelled. This runs in the same transaction, from
    the work order event inside the service.
- **One open work order per plan.** If the last job is still open when the next one is due, the planner waits; it
  does not pile up work orders nobody will do. The database enforces this too, with a partial unique index on
  `work_orders (plan_id)` for the open statuses.
- **The planner** is a `@Scheduled` job that runs every minute (`PLAN_CHECK_EVERY`). It handles each due plan in
  its own transaction and re-checks the plan on fresh data. If two instances run it at the same time, the plan's
  optimistic lock and the unique index let only one of them commit; the other logs a warning and the next run
  finds nothing to do. The scheduler does not need a lock (ShedLock or similar), because the database already
  guarantees the outcome.
- **Work orders opened by a plan are like any other work order.** Their actor is `maintenance-planner` and they
  carry the plan's id. This means:
  - alert-service, notification-service (the technician gets an "opened" notification) and the analytics
    warehouse handle them with no new code;
  - the metric `factorymind_workorders_created_total` gets `origin="PLAN"`;
  - the warehouse records the origin `PLAN`.
- An engineer can **open the next job now**, for example while the machine is stopped for another reason. The
  work order is then due today, and the rule of one open job per plan still holds.
- A plan is **paused, never deleted**, so its work orders keep their history. A plan resumed after a long pause
  starts again from today; it does not open the occurrences it missed one after another.
- The event on `factorymind.maintenance.work-orders` gets an optional `planId` field. This is the first change to
  a shared contract since ADR 0011, and it follows its rule: the field is added to the schema, it is optional, and
  the consumers ignore it until they need it. Their contract tests read a new example with it.

### Alternatives considered

- **A separate scheduling service** that sends "create a work order" commands. It would add a service, a topic
  and a two-step consistency problem (plan advanced, work order missing), for a feature that belongs to
  maintenance. Splitting it out later is easy, because the planner only talks to `WorkOrderService.openForPlan`.
- **Open all future occurrences in advance** (a year of work orders). Simple to show in a calendar, but the plan
  could no longer be edited without fixing dozens of open work orders, and the backlog would be mostly noise.
- **Plans by running hours from telemetry.** Possible once machines report a run signal or an hour meter. The
  plan model has room for it: a second trigger next to the interval in days.

## Consequences

- Routine servicing appears by itself, a few days ahead, assigned to the usual technician.
- Dashboards can tell planned from unplanned work, using the origin and `PLAN` in the warehouse.
- A plan cannot miss an occurrence silently: an overdue job stays open and red until someone closes it.
- The demo seeds five plans, one per state. The plan of M-101 is due on start, so its work order opens about a
  minute after the stack is up.
- Not covered: plans by running hours or by counter (cycles, kilometres), plans that cover several machines, and a
  calendar view of the coming jobs.
