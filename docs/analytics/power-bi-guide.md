# Power BI reports

Three reports on the analytics warehouse: **Executive Overview**, **Reliability & Maintenance** and
**AI Performance**. Power BI Desktop is free and runs on Windows.

## 1. Start the platform

```cmd
docker compose up -d --build
```

analytics-service loads the warehouse 20 seconds after start and then every 5 minutes. The first load also writes
180 days of simulated history. Check it on http://localhost:4200/analytics (or `GET /api/analytics/status`).

## 2. Connect Power BI Desktop

1. **Get data > PostgreSQL database**. Server `localhost:5432`, database `analytics_db`, mode **Import**.
2. Sign in with **Database** credentials: `POSTGRES_USER` / `POSTGRES_PASSWORD` from `.env`
   (`factorymind` / `factorymind` without `.env`). Local Postgres has no TLS: accept the unencrypted connection.
3. In the navigator, tick the views of schema `bi` only (Power BI shows them as `bi.Asset`, `bi.Calendar` …):
   `Asset`, `Calendar`, `Downtime`, `Failure Type`, `Maintenance`, `Prediction`, `Refresh`, `Technician`,
   `Telemetry`. **Load**.
4. Rename each query to drop the `bi ` prefix (Power Query: right-click > Rename): `Asset`, `Calendar`, …
   The measures use these names.

## 3. Model

**Model view.** Power BI already links most tables on equal column names; check that you have exactly these
(many-to-one, single direction, from the fact to the dimension):

| From (many) | To (one) | Active |
|---|---|---|
| Downtime[Asset Key] | Asset[Asset Key] | yes |
| Downtime[Date Key] | Calendar[Date Key] | yes |
| Downtime[Failure Type Key] | Failure Type[Failure Type Key] | yes |
| Maintenance[Asset Key] | Asset[Asset Key] | yes |
| Maintenance[Created Date Key] | Calendar[Date Key] | yes |
| Maintenance[Completed Date Key] | Calendar[Date Key] | **no** (used by MTTR) |
| Maintenance[Technician Key] | Technician[Technician Key] | yes |
| Maintenance[Failure Type Key] | Failure Type[Failure Type Key] | yes |
| Prediction[Asset Key] | Asset[Asset Key] | yes |
| Prediction[Date Key] | Calendar[Date Key] | yes |
| Telemetry[Asset Key] | Asset[Asset Key] | yes |
| Telemetry[Date Key] | Calendar[Date Key] | yes |

Then:

- Select **Calendar** > Table tools > **Mark as date table** > column `Date`.
- Calendar[Month]: **Sort by column** `Month Number`. Asset[Criticality]: sort by `Criticality Order`.
- Hide the key columns (`… Key`, `… Id`) in report view.

## 4. Measures

**DAX query view** > paste [`analytics/powerbi/measures.dax`](../../analytics/powerbi/measures.dax) > **Run**.
The result is one row per month: if it shows numbers, every measure works. Click **Update model with changes**
to add all the measures to the model. Format them: `%` measures as percentage (1 decimal), hours as decimal
(1 decimal), costs as currency €.

## 5. The three report pages

Add a slicer row at the top of every page: `Calendar[Date]` (between), `Asset[Factory]`, and `Maintenance[Source]`
or `Downtime[Source]` if you want to hide the simulated history. Sync the slicers across pages
(View > Sync slicers).

### Executive Overview
| Visual | Fields |
|---|---|
| 4 cards | `Availability %`, `Unplanned Downtime Hours`, `Failures`, `Maintenance Cost` |
| Line chart | X `Calendar[Year Month]`, Y `Availability %` |
| Clustered column | X `Calendar[Year Month]`, Y `Maintenance Cost`, legend `Maintenance[Maintenance Category]` |
| Bar chart | Y `Asset[Production Line]`, X `Unplanned Downtime Hours` |
| Card (small) | `Last Warehouse Load` |

### Reliability & Maintenance
| Visual | Fields |
|---|---|
| Matrix | Rows `Asset[Asset Label]`; values `Failures`, `Unplanned Downtime Hours`, `Availability %`, `MTBF (h)`, `MTTR (h)`, `Maintenance Cost` (conditional formatting on Availability) |
| Stacked column | X `Calendar[Year Month]`, Y `Corrective Orders` and `Planned Orders` |
| Bar chart | Y `Failure Type[Failure Type]`, X `Failures` |
| Bar chart | Y `Technician[Technician]`, X `MTTR (h)` |
| Scatter | Details `Asset[Asset Code]`, X `MTBF (h)`, Y `MTTR (h)`, size `Maintenance Cost` (top-left = fails often and takes long to fix) |

### AI Performance
| Visual | Fields |
|---|---|
| 4 cards | `Precision %`, `Recall %`, `Avg Lead Time (h)`, `False Alarms` |
| Clustered column | X `Prediction[Model Version]`, Y `Precision %`, `Recall %` |
| Line chart | X `Calendar[Year Month]`, Y `Precision %`, `Recall %` |
| Donut | Legend `Prediction[Outcome]`, values `True Positives`… or count of `Prediction Id` |
| Table | `Asset[Asset Code]`, `Prediction[Predicted At]`, `Prediction[Main Sensor]`, `Prediction[Risk Score]`, `Prediction[Outcome]`, `Prediction[Lead Time Hours]` |

Optional fourth page, **Sensor trends**: line chart X `Telemetry[Hour]`, Y `Avg Value` and `Peak Value`, with
slicers on `Asset[Asset Code]` and `Telemetry[Sensor]`. The values climb in the day before each failure.

## 6. Save in Git

File > Options > Preview features > **Power BI Project (.pbip) save option**. Then **File > Save as** >
`analytics/powerbi/FactoryMind.pbip`. Power BI writes the model as TMDL text files and the report as JSON, which
Git can diff. `.gitignore` already leaves out the local cache (`.pbi/localSettings.json`, `.pbi/cache.abf`).
Take screenshots of the three pages for `docs/demo/`.

## 7. Publish and link (optional)

Publishing to the Power BI service needs a work or school account (Power BI Pro or a trial). The service reaches a
database on your PC through an **on-premises data gateway**. Once the report is published, set
`POWERBI_REPORT_URL` in `.env` to its link: the analytics page then shows **Open Power BI report**. Embedding the
report inside the Angular app (Power BI Embedded) needs a paid capacity, so FactoryMind links to it instead and
shows its own KPIs from the same warehouse.

## A read-only login for Power BI

For anything beyond your PC, give Power BI its own login that can only read the `bi` views:

```sql
-- psql -h localhost -U factorymind -d analytics_db
CREATE ROLE powerbi LOGIN PASSWORD 'choose-a-password';
GRANT CONNECT ON DATABASE analytics_db TO powerbi;
GRANT USAGE ON SCHEMA bi TO powerbi;
GRANT SELECT ON ALL TABLES IN SCHEMA bi TO powerbi;
-- The views read dw and staging with the rights of their owner, so powerbi needs nothing else.
```
