-- The Power BI semantic model (analytics/powerbi) imports these views, nothing else. They carry the names
-- the report shows, so renaming a column in dw only means changing the view.

CREATE VIEW bi."Calendar" AS
SELECT date_key      AS "Date Key",
       date          AS "Date",
       year          AS "Year",
       quarter_name  AS "Quarter",
       month         AS "Month Number",
       month_name    AS "Month",
       year_month    AS "Year Month",
       month_start   AS "Month Start",
       iso_week      AS "ISO Week",
       day_of_week   AS "Day of Week Number",
       day_name      AS "Day of Week",
       is_weekend    AS "Is Weekend"
FROM dw.dim_date
-- From the first day with data to today: availability counts every day of the selection as operating time.
WHERE date BETWEEN (SELECT coalesce(least((SELECT min(event_start)::date FROM dw.fact_downtime),
                                          (SELECT min(created_at)::date FROM dw.fact_maintenance),
                                          (SELECT min(hour_start)::date FROM dw.fact_telemetry_hourly)),
                                    current_date))
               AND current_date;

CREATE VIEW bi."Asset" AS
SELECT asset_key         AS "Asset Key",
       asset_code        AS "Asset Code",
       asset_name        AS "Asset",
       asset_code || ' ' || asset_name AS "Asset Label",
       manufacturer      AS "Manufacturer",
       model             AS "Model",
       criticality       AS "Criticality",
       CASE criticality WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 ELSE 4 END
                         AS "Criticality Order",
       installed_at      AS "Installed On",
       current_status    AS "Current Status",
       line_name         AS "Production Line",
       factory_name      AS "Factory",
       factory_location  AS "Factory Location",
       removed           AS "Removed"
FROM dw.dim_asset;

CREATE VIEW bi."Failure Type" AS
SELECT failure_type_key AS "Failure Type Key",
       name             AS "Failure Type",
       category         AS "Failure Category"
FROM dw.dim_failure_type;

CREATE VIEW bi."Technician" AS
SELECT technician_key AS "Technician Key",
       username       AS "Technician"
FROM dw.dim_technician;

CREATE VIEW bi."Maintenance" AS
SELECT work_order_id       AS "Work Order Id",
       work_order_code     AS "Work Order",
       asset_key           AS "Asset Key",
       technician_key      AS "Technician Key",
       failure_type_key    AS "Failure Type Key",
       created_date_key    AS "Created Date Key",
       completed_date_key  AS "Completed Date Key",
       maintenance_type    AS "Maintenance Type",
       initcap(category)   AS "Maintenance Category",
       origin              AS "Origin",
       priority            AS "Priority",
       status              AS "Status",
       created_at          AS "Created At",
       started_at          AS "Started At",
       completed_at        AS "Completed At",
       response_hours      AS "Response Hours",
       repair_hours        AS "Repair Hours",
       restore_hours       AS "Restore Hours",
       parts_cost          AS "Parts Cost",
       labor_cost          AS "Labor Cost",
       parts_cost + labor_cost AS "Total Cost",
       source              AS "Source"
FROM dw.fact_maintenance;

CREATE VIEW bi."Downtime" AS
SELECT downtime_id       AS "Downtime Id",
       date_key          AS "Date Key",
       asset_key         AS "Asset Key",
       failure_type_key  AS "Failure Type Key",
       initcap(downtime_type) AS "Downtime Type",
       event_start       AS "Event Start",
       event_end         AS "Event End",
       is_event_start    AS "Is Event Start",
       hours             AS "Hours",
       source            AS "Source"
FROM dw.fact_downtime;

CREATE VIEW bi."Prediction" AS
SELECT prediction_id     AS "Prediction Id",
       date_key          AS "Date Key",
       asset_key         AS "Asset Key",
       model_version     AS "Model Version",
       CASE outcome WHEN 'TRUE_POSITIVE' THEN 'True positive' WHEN 'FALSE_POSITIVE' THEN 'False alarm'
                    WHEN 'PENDING' THEN 'Pending' ELSE 'Missed failure' END AS "Outcome",
       predicted_at      AS "Predicted At",
       risk_score        AS "Risk Score",
       main_sensor_type  AS "Main Sensor",
       failure_at        AS "Failure At",
       lead_time_hours   AS "Lead Time Hours",
       source            AS "Source"
FROM dw.fact_prediction;

CREATE VIEW bi."Telemetry" AS
SELECT asset_key          AS "Asset Key",
       date_key           AS "Date Key",
       hour_start         AS "Hour",
       hour_of_day        AS "Hour of Day",
       sensor_type        AS "Sensor",
       unit               AS "Unit",
       readings           AS "Readings",
       avg_value          AS "Average Value",
       min_value          AS "Min Value",
       max_value          AS "Max Value",
       warning_readings   AS "Warning Readings",
       critical_readings  AS "Critical Readings",
       source             AS "Source"
FROM dw.fact_telemetry_hourly;

CREATE VIEW bi."Refresh" AS
SELECT max(finished_at) AS "Last Load", count(*) AS "Runs"
FROM dw.etl_runs WHERE status = 'SUCCEEDED';
