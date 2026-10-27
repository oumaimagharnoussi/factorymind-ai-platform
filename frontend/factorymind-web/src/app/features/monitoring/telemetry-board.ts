import { ReadingPoint, ReadingStatus, SensorSnapshot, SensorType } from '../../core/api/models';

/** How many recent values each sparkline keeps (about 5 minutes at one reading every 3 seconds). */
export const HISTORY_POINTS = 100;

export interface SensorView {
  snapshot: SensorSnapshot;
  history: number[];
}

const SEVERITY: Record<ReadingStatus, number> = { CRITICAL: 3, WARNING: 2, UNKNOWN: 1, NORMAL: 0 };
const SENSOR_ORDER: SensorType[] = ['TEMPERATURE', 'VIBRATION', 'PRESSURE', 'ROTATION_SPEED'];

export function worst(statuses: ReadingStatus[]): ReadingStatus {
  return statuses.reduce<ReadingStatus>((a, b) => (SEVERITY[b] > SEVERITY[a] ? b : a), 'NORMAL');
}

export function severity(status: ReadingStatus): number {
  return SEVERITY[status];
}

const key = (assetId: string, sensorType: SensorType) => `${assetId}:${sensorType}`;

/**
 * The live picture of every sensor, as an immutable map so Angular signals see each change.
 * Built from the REST snapshot and history, then moved forward by stream events.
 */
export class TelemetryBoard {
  private constructor(readonly sensors: ReadonlyMap<string, SensorView>) {}

  static from(latest: SensorSnapshot[], history: ReadingPoint[]): TelemetryBoard {
    const values = new Map<string, number[]>();
    for (const point of history) {
      const k = key(point.assetId, point.sensorType);
      values.set(k, [...(values.get(k) ?? []), point.value]);
    }
    const sensors = new Map<string, SensorView>();
    for (const snapshot of latest) {
      const k = key(snapshot.assetId, snapshot.sensorType);
      sensors.set(k, {
        snapshot,
        history: (values.get(k) ?? [snapshot.value]).slice(-HISTORY_POINTS),
      });
    }
    return new TelemetryBoard(sensors);
  }

  /** A newer reading replaces the latest value and is appended to the sparkline; an older one is ignored. */
  apply(snapshot: SensorSnapshot): TelemetryBoard {
    const k = key(snapshot.assetId, snapshot.sensorType);
    const current = this.sensors.get(k);
    if (current && current.snapshot.recordedAt >= snapshot.recordedAt) {
      return this;
    }
    const history = [...(current?.history ?? []), snapshot.value].slice(-HISTORY_POINTS);
    const sensors = new Map(this.sensors);
    sensors.set(k, { snapshot, history });
    return new TelemetryBoard(sensors);
  }

  /** Sensors grouped by machine, each machine's sensors in a fixed order. */
  byAsset(): Map<string, SensorView[]> {
    const groups = new Map<string, SensorView[]>();
    for (const view of this.sensors.values()) {
      groups.set(view.snapshot.assetId, [...(groups.get(view.snapshot.assetId) ?? []), view]);
    }
    for (const views of groups.values()) {
      views.sort(
        (a, b) =>
          SENSOR_ORDER.indexOf(a.snapshot.sensorType) - SENSOR_ORDER.indexOf(b.snapshot.sensorType),
      );
    }
    return groups;
  }

  count(status: ReadingStatus): number {
    return [...this.sensors.values()].filter((view) => view.snapshot.status === status).length;
  }
}
