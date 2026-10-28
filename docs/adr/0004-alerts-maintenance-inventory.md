# ADR 0004: Debounced alerts, one work order per incident, spare parts booked over REST

- Status: accepted
- Date: 2026-10-27

## Context

telemetry-service publishes every change of sensor level on `factorymind.telemetry.sensor-status`. A value
that hovers around a limit flips between NORMAL and WARNING many times a minute (see ADR 0003). The plant needs
one alert per real problem, a work order that a technician can follow, and a stock of spare parts that never
goes negative even when two technicians take the last bearing at the same time.

## Decision

### alert-service (8083, `alert_db`)

- **Debounce with an on-delay and an off-delay**, as alarm management standards (ISA-18.2) recommend. Each
  sensor has one timer per level (WARNING, CRITICAL), stored in `sensor_watches`:
  - a level is **raised** once the sensor has spent `ALERT_RAISE_AFTER` (15 s) above it. Time above the level
    adds up across short dips, so a sensor flapping around a limit is still raised;
  - a level is **cleared** only after `ALERT_CLEAR_AFTER` (30 s) continuously below it.
  - Status changes only arrive when the level changes, so a scheduler (`ALERT_CHECK_INTERVAL`, 2 s) re-checks
    the sensors that are in the middle of an episode. Out-of-order messages (older than the last one seen) are
    ignored.
- **One unresolved alert per sensor** (partial unique index). If the sensor goes bad again before someone
  resolves the alert, the same alert is reused (`RETRIGGERED`, occurrences + 1) instead of opening a new one.
  Severity only goes up (WARNING to CRITICAL = `ESCALATED`) so the alert keeps the worst level reached.
- **Life cycle**: `OPEN` → `ACKNOWLEDGED` → `RESOLVED`. *Active* (the condition is still there) is a separate
  flag: an alert cannot be resolved while it is active. Any role acknowledges; ENGINEER and ADMIN resolve.
- Every change is published on `factorymind.alert.alerts` (key asset id) after commit and pushed to the
  browser on `GET /api/alerts/stream` (SSE).

### maintenance-service (8084, `maintenance_db`)

- **A critical alert opens an URGENT corrective work order.** If the machine already has an open work order
  created from an alert, the new alert is linked to it (`ALERT_LINKED`) instead: one failing machine is one
  incident, not one ticket per sensor. An alert belongs to at most one work order (`work_order_alerts`
  primary key), which also makes the Kafka consumer idempotent.
- Status machine: `OPEN` → `IN_PROGRESS` / `ON_HOLD` / `CANCELLED`; `IN_PROGRESS` → `ON_HOLD` / `COMPLETED` /
  `CANCELLED`; `ON_HOLD` → `IN_PROGRESS` / `CANCELLED`. Completing needs a resolution note. Work orders are never
  deleted; every change is written to `work_order_activities`.
- Work order changes are published on `factorymind.maintenance.work-orders`. alert-service listens: linked
  alerts are acknowledged automatically, and resolved when the work order is completed and the sensor is back
  to normal (immediately, or as soon as the off-delay ends).
- MTTR on the dashboard = average time from creation to completion of the work orders closed in the last 30 days.

### inventory-service (8086, `inventory_db`)

- `spare_parts` keeps the quantity on hand (`CHECK >= 0`); `stock_movements` is the ledger (receipt, issue,
  return, adjustment, with the quantity after the move and who did it).
- **Pessimistic row lock** (`SELECT … FOR UPDATE`) on the part for every stock move. Stock is a counter that
  many people change at the same time; optimistic locking would turn every race into a retry for the user.
- Receiving and editing parts: ENGINEER and ADMIN. Stock count adjustments and deletes: ADMIN only.

### Using a part on a work order

- maintenance-service calls inventory-service **synchronously** (`POST /api/inventory/parts/{id}/issues`) with
  the caller's token: the technician must know at once whether the part is in stock, which an event cannot say.
  Timeouts are 2 s to connect and 5 s to read; if inventory is down the answer is 503 and nothing is booked.
- Each issue carries a **request id**; inventory replays the stored movement for a request id it has already
  seen, so a retry never takes the part twice.
- If saving the part line fails after the stock was taken, maintenance gives the part back (compensation)
  with a request id derived from the line id. Removing a part line from a work order returns it to stock the
  same way.

### Front end

New screens: **Alerts** (live, acknowledge, resolve, open a work order prefilled from the alert), **Work
orders** (list, form, detail with status buttons, parts and history), **Spare parts** (catalogue, part page
with receipts, stock counts and movements). The dashboard adds open alerts, open and urgent work orders, MTTR
and parts to reorder.

## Consequences

- Alerts arrive 15 s after a sensor goes bad: the price of not paging people for noise. Both delays are
  configuration, per environment.
- Kafka events are still published after commit, as in ADR 0003; a lost event is repaired by the next status
  change of that sensor, and an outbox remains the next step.
- The parts saga lives in one method of maintenance-service. If the compensation itself fails (inventory down
  at that exact moment) the error is logged with the part and the work order and the stock must be corrected by hand; a retry
  queue would remove that manual step.
- Services keep only ids of each other's data (asset id, part id, work order id); the front end looks up the
  machine names from asset-service.
