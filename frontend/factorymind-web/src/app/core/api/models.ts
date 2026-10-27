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
