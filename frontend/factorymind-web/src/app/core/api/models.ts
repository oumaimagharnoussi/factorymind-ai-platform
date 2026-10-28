export type AssetStatus = 'OPERATIONAL' | 'WARNING' | 'STOPPED' | 'MAINTENANCE';
export type Criticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type LineStatus = 'RUNNING' | 'IDLE' | 'STOPPED';
export type SensorType = 'TEMPERATURE' | 'VIBRATION' | 'PRESSURE' | 'ROTATION_SPEED';

export const ASSET_STATUSES: AssetStatus[] = ['OPERATIONAL', 'WARNING', 'STOPPED', 'MAINTENANCE'];
export const CRITICALITIES: Criticality[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const SENSOR_TYPES: SensorType[] = ['TEMPERATURE', 'VIBRATION', 'PRESSURE', 'ROTATION_SPEED'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Factory {
  id: string;
  name: string;
  location: string;
  timezone: string;
  lineCount: number;
  createdAt: string;
}

export interface ProductionLine {
  id: string;
  factoryId: string;
  factoryName: string;
  name: string;
  status: LineStatus;
  assetCount: number;
}

export interface Asset {
  id: string;
  assetCode: string;
  name: string;
  lineId: string;
  lineName: string;
  factoryId: string;
  factoryName: string;
  manufacturer: string | null;
  model: string | null;
  criticality: Criticality;
  installedAt: string | null;
  status: AssetStatus;
  updatedAt: string;
  version: number;
}

export interface AssetSummary {
  total: number;
  byStatus: Record<AssetStatus, number>;
  byCriticality: Record<Criticality, number>;
}

export interface Sensor {
  id: string;
  assetId: string;
  sensorType: SensorType;
  unit: string;
  warningThreshold: number;
  criticalThreshold: number;
}

export interface AssetQuery {
  q?: string;
  status?: AssetStatus | null;
  criticality?: Criticality | null;
  factoryId?: string | null;
  page?: number;
  size?: number;
}

export interface CreateAssetRequest {
  assetCode: string;
  name: string;
  lineId: string;
  manufacturer?: string | null;
  model?: string | null;
  criticality: Criticality;
  installedAt?: string | null;
}

export interface CreateSensorRequest {
  sensorType: SensorType;
  unit: string;
  warningThreshold: number;
  criticalThreshold: number;
}

/** RFC 9457 problem detail returned by every backend error. */
export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

// Telemetry service

export type ReadingStatus = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'UNKNOWN';

/** Latest value of one sensor with the limits it is judged against (null when telemetry has no limits yet). */
export interface SensorSnapshot {
  assetId: string;
  sensorType: SensorType;
  value: number;
  unit: string | null;
  status: ReadingStatus;
  recordedAt: string;
  statusSince: string;
  warningThreshold: number | null;
  criticalThreshold: number | null;
}

export interface ReadingPoint {
  assetId: string;
  sensorType: SensorType;
  value: number;
  status: ReadingStatus;
  recordedAt: string;
}

export interface SensorStatusChange {
  assetId: string;
  sensorType: SensorType;
  previousStatus: ReadingStatus | null;
  status: ReadingStatus;
  value: number;
  unit: string | null;
  warningThreshold: number | null;
  criticalThreshold: number | null;
  recordedAt: string;
}

export type TelemetryEvent =
  | { type: 'reading'; data: SensorSnapshot }
  | { type: 'status'; data: SensorStatusChange };

// Alert service

export type AlertSeverity = 'WARNING' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
export type AlertEventType =
  | 'RAISED'
  | 'ESCALATED'
  | 'RETRIGGERED'
  | 'CLEARED'
  | 'ACKNOWLEDGED'
  | 'RESOLVED'
  | 'WORK_ORDER_UPDATED';

/** One abnormal episode of one sensor. `active` is false once the sensor has been back to normal long enough. */
export interface Alert {
  id: string;
  assetId: string;
  sensorType: SensorType;
  severity: AlertSeverity;
  status: AlertStatus;
  active: boolean;
  currentStatus: ReadingStatus;
  lastValue: number | null;
  unit: string | null;
  warningThreshold: number | null;
  criticalThreshold: number | null;
  occurrences: number;
  raisedAt: string;
  lastRaisedAt: string;
  escalatedAt: string | null;
  clearedAt: string | null;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  resolutionNote: string | null;
  workOrderId: string | null;
  workOrderCode: string | null;
  workOrderStatus: WorkOrderStatus | null;
  version: number;
}

export interface AlertSummary {
  critical: number;
  warning: number;
  active: number;
  unacknowledged: number;
}

export interface AlertQuery {
  statuses?: AlertStatus[];
  severity?: AlertSeverity | null;
  assetId?: string | null;
  page?: number;
  size?: number;
}

export interface AlertEvent {
  type: AlertEventType;
  alert: Alert;
}

// Maintenance service

export type WorkOrderType = 'CORRECTIVE' | 'PREVENTIVE' | 'INSPECTION';
export type WorkOrderPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
export type WorkOrderStatus = 'OPEN' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED' | 'CANCELLED';
export type ActivityType = 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'ALERT_LINKED' | 'PART_USED' | 'PART_RETURNED';

export const WORK_ORDER_TYPES: WorkOrderType[] = ['CORRECTIVE', 'PREVENTIVE', 'INSPECTION'];
export const WORK_ORDER_PRIORITIES: WorkOrderPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
export const WORK_ORDER_STATUSES: WorkOrderStatus[] = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];
export const OPEN_WORK_ORDER_STATUSES: WorkOrderStatus[] = ['OPEN', 'IN_PROGRESS', 'ON_HOLD'];

/** A row of the work order list. */
export interface WorkOrderSummary {
  id: string;
  code: string;
  assetId: string;
  alertIds: string[];
  type: WorkOrderType;
  priority: WorkOrderPriority;
  status: WorkOrderStatus;
  title: string;
  assignee: string | null;
  dueDate: string | null;
  overdue: boolean;
  requestedBy: string;
  createdAt: string;
  completedAt: string | null;
}

export interface PartLine {
  id: string;
  partId: string;
  partNumber: string;
  partName: string;
  unit: string;
  quantity: number;
  unitCost: number;
  cost: number;
  issuedBy: string;
  issuedAt: string;
}

export interface WorkOrderActivity {
  type: ActivityType;
  message: string;
  actor: string;
  occurredAt: string;
}

export interface WorkOrder extends WorkOrderSummary {
  /** Statuses this work order can move to now; the screen only offers these. */
  nextStatuses: WorkOrderStatus[];
  description: string | null;
  updatedAt: string;
  startedAt: string | null;
  resolution: string | null;
  parts: PartLine[];
  partsCost: number;
  activities: WorkOrderActivity[];
  version: number;
}

export interface MaintenanceSummary {
  byStatus: Record<WorkOrderStatus, number>;
  open: number;
  overdue: number;
  urgentOpen: number;
  mttrHours: number | null;
  completedLast30Days: number;
}

export interface WorkOrderQuery {
  statuses?: WorkOrderStatus[];
  priority?: WorkOrderPriority | null;
  assetId?: string | null;
  q?: string;
  page?: number;
  size?: number;
}

export interface CreateWorkOrderRequest {
  assetId: string;
  alertId?: string | null;
  type: WorkOrderType;
  priority: WorkOrderPriority;
  title: string;
  description?: string | null;
  assignee?: string | null;
  dueDate?: string | null;
}

// Inventory service

export type PartCategory =
  | 'MECHANICAL'
  | 'ELECTRICAL'
  | 'HYDRAULIC'
  | 'PNEUMATIC'
  | 'LUBRICANT'
  | 'FILTER'
  | 'SENSOR'
  | 'CONSUMABLE';
export type MovementType = 'RECEIPT' | 'ISSUE' | 'RETURN' | 'ADJUSTMENT';

export const PART_CATEGORIES: PartCategory[] = [
  'MECHANICAL',
  'ELECTRICAL',
  'HYDRAULIC',
  'PNEUMATIC',
  'LUBRICANT',
  'FILTER',
  'SENSOR',
  'CONSUMABLE',
];

export interface SparePart {
  id: string;
  partNumber: string;
  name: string;
  description: string | null;
  category: PartCategory;
  unit: string;
  quantityOnHand: number;
  minimumStock: number;
  lowStock: boolean;
  unitCost: number;
  stockValue: number;
  location: string | null;
  updatedAt: string;
  version: number;
}

export interface StockMovement {
  id: string;
  partId: string;
  partNumber: string;
  partName: string;
  unit: string;
  type: MovementType;
  quantity: number;
  change: number;
  quantityAfter: number;
  unitCost: number;
  workOrderId: string | null;
  workOrderCode: string | null;
  reference: string | null;
  note: string | null;
  performedBy: string;
  performedAt: string;
}

export interface InventorySummary {
  parts: number;
  lowStock: number;
  outOfStock: number;
  stockValue: number;
}

export interface PartQuery {
  q?: string;
  category?: PartCategory | null;
  lowStock?: boolean | null;
  page?: number;
  size?: number;
}

export interface CreatePartRequest {
  partNumber: string;
  name: string;
  description?: string | null;
  category: PartCategory;
  unit: string;
  minimumStock: number;
  unitCost: number;
  location?: string | null;
}

/** Probability that a sensor reaches its critical limit within the model's horizon: LOW < 0.4 ≤ MEDIUM < 0.7 ≤ HIGH. */
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface SensorRisk {
  sensorType: SensorType;
  unit: string | null;
  currentValue: number;
  failureProbability: number;
  riskLevel: RiskLevel;
  /** Change per hour, in the sensor's unit. */
  trendPerHour: number;
  /** When the trend meets the critical limit; null when the sensor is not clearly rising. */
  hoursToCritical: number | null;
  /** Plain-language reasons, worst first. */
  drivers: string[];
}

export interface MachineRisk {
  assetId: string;
  riskScore: number;
  riskLevel: RiskLevel;
  horizonHours: number;
  mainSensorType: SensorType;
  hoursToCritical: number | null;
  sensors: SensorRisk[];
  modelVersion: string;
  assessedAt: string;
}

export interface RiskPoint {
  assessedAt: string;
  riskScore: number;
  riskLevel: RiskLevel;
}

export interface BinaryMetrics {
  precision: number;
  recall: number;
}

export interface ModelCard {
  modelVersion: string;
  algorithm: string;
  trainedAt: string;
  horizonHours: number;
  decisionThreshold: number;
  trainingData: { source: string; sensorLives: number; windows: number; positiveShare: number; testWindows: number };
  features: string[];
  featureImportances: Record<string, number>;
  metrics: {
    model: BinaryMetrics & { rocAuc: number; averagePrecision: number; brierScore: number };
    warningThresholdRule: BinaryMetrics;
    /** Share of upcoming failures flagged while every sensor was still below its warning limit. */
    earlyDetectionRate: number | null;
  };
}

export interface MachineReport {
  assetId: string;
  machine: string;
  /** Markdown. */
  text: string;
  /** "anthropic:claude-sonnet-5-5", "ollama:llama3.2" or "rules". */
  generatedBy: string;
  generatedAt: string;
}

export interface AssistantStatus {
  enabled: boolean;
  model: string;
}

export interface AssistantAnswer {
  answer: string;
  generatedBy: string;
  answeredAt: string;
}
