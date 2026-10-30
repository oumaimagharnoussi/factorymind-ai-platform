package com.factorymind.analytics.etl;

import com.factorymind.analytics.etl.SourceRows.AssetRow;
import com.factorymind.analytics.etl.SourceRows.AssetSensorRow;
import com.factorymind.analytics.etl.SourceRows.CriticalAlertRow;
import com.factorymind.analytics.etl.SourceRows.RiskAssessmentRow;
import com.factorymind.analytics.etl.SourceRows.TelemetryHourRow;
import com.factorymind.analytics.etl.SourceRows.WorkOrderAlertRow;
import com.factorymind.analytics.etl.SourceRows.WorkOrderRow;
import java.time.Instant;
import java.util.List;

/**
 * Everything one run read from the service databases.
 *
 * @param telemetryUpTo start of the hour being read when the extract ran: the next run reads again from there
 */
public record Extract(List<AssetRow> assets, List<AssetSensorRow> assetSensors, List<WorkOrderRow> workOrders,
                      List<WorkOrderAlertRow> workOrderAlerts, List<CriticalAlertRow> criticalAlerts,
                      List<RiskAssessmentRow> riskAssessments, List<TelemetryHourRow> telemetryHours,
                      Instant telemetryUpTo) {
}
