# ADR 0003: Kafka for sensor readings, telemetry-service with its own copy of the alarm limits

- Status: accepted
- Date: 2026-10-27

## Context

Machines send a reading per sensor every few seconds. The dashboard must show them live, judge each one
against the sensor's warning and critical limits, and later the alert, maintenance and AI services must react
to the same data. The limits are owned by asset-service (`sensor_definitions` in `asset_db`), and a service
never reads another service's database.

## Decision

- **Kafka 4 in KRaft mode** (one broker in Compose, no ZooKeeper) carries every event. Automatic topic creation
  is off; each service declares the topics it owns with their partitions and settings.
- **Topics**

  | Topic | Owner | Key | Content |
  |---|---|---|---|
  | `factorymind.asset.sensor-definitions` | asset-service | sensor id | Current limits of one sensor; `null` value (tombstone) when removed. **Compacted.** |
  | `factorymind.telemetry.readings` | telemetry-service | asset id | One measurement, with a producer-chosen `readingId`. 3 partitions. |
  | `factorymind.telemetry.sensor-status` | telemetry-service | asset id | A sensor moved between NORMAL, WARNING and CRITICAL. |
  | `<topic>-dlt` | telemetry-service | same | Records that still failed after two retries. |

- **Event-carried state transfer for the limits.** asset-service publishes a sensor's limits after the
  transaction that changed them commits (`@TransactionalEventListener`), and republishes the whole catalogue
  on startup. telemetry-service reads the compacted topic from the beginning on every start and keeps
  `sensor_thresholds` in `telemetry_db`. Judging a reading never needs a call to asset-service.
- **Ordering and duplicates.** Readings are keyed by asset id, so all readings of one machine are processed
  in order by one consumer. Kafka delivers at least once; `readingId` is the primary key of `sensor_readings`,
  so a redelivered reading is skipped. A reading older than the sensor's current state is stored in the
  history but does not move the live state back.
- **Only changes leave the service.** Every reading is stored, but only status changes are published to
  `sensor-status`: that is what the alert service (weeks 8–9) will turn into alerts and work orders.
- **Poison messages** go to a dead-letter topic after two retries instead of blocking the partition. The
  original bytes are kept so they can be inspected in Kafka UI and replayed.
- **Live push with Server-Sent Events.** The dashboard opens `GET /api/telemetry/stream` through the gateway.
  SSE is one-way, plain HTTP and goes through the gateway and nginx unchanged; WebSocket would add a second
  protocol for no gain. `EventSource` cannot send an `Authorization` header, so the Angular app reads the
  stream with `fetch`. Each stream closes after 15 minutes and the app reconnects with a fresh token.
- **Retention.** Raw readings are kept 3 days (`TELEMETRY_RETENTION`). Long-term trends belong in the
  analytics pipeline, not in the operational database.
- **Simulator.** In the demo, telemetry-service itself publishes fake readings to the readings topic, so the
  data follows exactly the path real plant gateways would use (Kafka, or `POST /api/telemetry/readings`).

## Consequences

- Publishing after commit can lose a change if Kafka is down at that moment; the startup republish repairs
  it. A transactional outbox table would close that gap and is the next step if limits change often.
- A noisy value close to a limit makes a sensor flap between two levels. Readings stay honest; the alert
  service will debounce (several readings in a row, or a small hysteresis band) before raising an alert.
- Asset ids and sensor types are a contract between services: a new `SensorType` must be added to
  telemetry-service before asset-service uses it (otherwise those records end up in the dead-letter topic).
- Each telemetry instance pushes only the readings it processed itself. That is complete with one instance;
  to scale out, the live push would read `readings` with a consumer group per instance (or go through Redis),
  while storage keeps the shared `telemetry-service` group.
- `docker compose up` now also needs Kafka; `db-init` creates missing databases on every start, so volumes
  from earlier phases get `telemetry_db` without being wiped.
