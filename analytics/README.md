# Analytics

The analytics layer of FactoryMind: a star-schema warehouse loaded from the services, and the Power BI reports on it.

| Where | What |
|---|---|
| `backend/analytics-service` | The pipeline (extract from the service databases, load the warehouse every 5 min), the demo history, the KPI API behind `/analytics` in the web app |
| `analytics_db`, schema `dw` | The star schema: `dim_date`, `dim_asset`, `dim_failure_type`, `dim_technician`; `fact_maintenance`, `fact_downtime`, `fact_prediction`, `fact_telemetry_hourly` |
| `analytics_db`, schema `bi` | The views Power BI imports, with report-friendly names |
| `powerbi/measures.dax` | The DAX measures (availability, MTBF, MTTR, cost, precision, recall …) |
| `powerbi/FactoryMind.pbip` | The Power BI project once saved from Desktop (model as TMDL, report as JSON) |
| [`docs/analytics/kpi-definitions.md`](../docs/analytics/kpi-definitions.md) | What each KPI means, exactly |
| [`docs/analytics/power-bi-guide.md`](../docs/analytics/power-bi-guide.md) | Connect Power BI, build the three reports |
| [`docs/adr/0007-analytics-warehouse-power-bi.md`](../docs/adr/0007-analytics-warehouse-power-bi.md) | Why it is built this way |

```
asset_db ─────────┐
maintenance_db ───┤  analytics-service         analytics_db
alert_db ─────────┼─► extract (read-only) ──► staging ──► dw (star schema) ──► bi (views) ──► Power BI
insights_db ──────┤    every 5 min                         │
telemetry_db ─────┘                                        └──► /api/analytics ──► Angular /analytics
```
