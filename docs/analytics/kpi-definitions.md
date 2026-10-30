# KPI definitions

The same KPI can be computed in several ways, so FactoryMind writes down the one it uses. The Power BI measures
(`analytics/powerbi/measures.dax`) and the analytics API (`KpiService`) follow these definitions and give the same
numbers for the same period and factory.

All times are UTC. A day is a UTC calendar day (`date_key`).

## Building blocks

| Term | Definition | Source |
|---|---|---|
| **Failure** | A critical alert (a sensor above its critical limit for 15 s), or a corrective work order opened by hand. Counted once, on the day it started. | alert-service, maintenance-service |
| **Failure time** | Time of the first critical alert answered by the corrective work order (`escalated_at`, else `raised_at`); the order's creation time when no alert is linked. | |
| **Unplanned downtime** | From the failure time until the corrective work order is completed. Still running at load time: counted up to the load. | maintenance-service |
| **Planned downtime** | A preventive work order, from started to completed. Inspections do not stop the machine. | maintenance-service |
| **Calendar hours** | Machines in scope × days in the period × 24. The plants are meant to run around the clock. Days before the first data and after today are not counted. | asset-service |
| **Operating hours** | Calendar hours − planned downtime. | |
| **Uptime** | Operating hours − unplanned downtime. | |

Downtime is cut per calendar day, so a stop from 22:00 to 02:00 adds 2 h to each day.

## KPIs

| KPI | Formula | Notes |
|---|---|---|
| **Availability %** | Uptime ÷ operating hours | Planned stops do not lower availability. |
| **Failures** | Number of unplanned downtime events that started in the period | |
| **MTBF (h)** | Uptime ÷ failures | Mean time between failures. Empty when there is no failure. |
| **MTTR (h)** | Average of (completed − failure time) over corrective orders **completed** in the period | Mean time to *restore*: waiting for a technician or a part counts, because the machine is down. Response time and hands-on repair time are separate measures. |
| **Maintenance cost** | Parts + labour of the work orders **opened** in the period (cancelled excluded) | Parts: quantity × unit cost booked on the order. Labour: hands-on hours (started → completed) × `ANALYTICS_LABOR_RATE` (35 €/h by default), the only cost the services do not record. |
| **Planned share %** | Planned orders ÷ (planned + corrective) | Planned = preventive and inspection orders. |
| **Cost per failure** | Cost of corrective orders ÷ failures | |

## AI performance

The model rates a machine HIGH when it expects a sensor to reach its critical limit within 24 h. A **prediction**
is a HIGH episode: from the moment a machine turns HIGH until it leaves HIGH.

| Outcome | Rule |
|---|---|
| **True positive** | The machine fails within 24 h after the prediction started. *Lead time* = failure − prediction. |
| **False alarm** | 24 h passed without a failure. |
| **Pending** | Less than 24 h since the prediction, no failure yet. Left out of precision. |
| **Missed failure** | A failure with no prediction started in the 24 h before it. Only counted once the model was running. |

| KPI | Formula |
|---|---|
| **Precision %** | True positives ÷ (true positives + false alarms) |
| **Recall % (failures predicted)** | True positives ÷ (true positives + missed failures) |
| **False alarm rate %** | False alarms ÷ (true positives + false alarms) |
| **Average lead time (h)** | Mean lead time of true positives |

A failure that the inspection prevents shows up as a false alarm: the model was right, but nothing failed. This is
the known limit of measuring a predictive maintenance model on the plant it protects; the model card in
ai-insights-service gives the offline metrics on held-out data.

## Demo data

The months before the first start are simulated (`source = 'SIMULATED'`, model versions `rf-24h-sim-v1` and
`rf-24h-sim-v2`), so the reports have trends to show. Every report can filter on `Source`. The numbers are not
real plant data.
