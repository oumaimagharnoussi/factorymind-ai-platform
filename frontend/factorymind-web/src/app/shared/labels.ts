import { SensorType } from '../core/api/models';

const SENSOR_LABELS: Record<SensorType, string> = {
  TEMPERATURE: 'Temperature',
  VIBRATION: 'Vibration',
  PRESSURE: 'Pressure',
  ROTATION_SPEED: 'Rotation speed',
};

export function sensorLabel(type: SensorType): string {
  return SENSOR_LABELS[type] ?? type;
}

/** IN_PROGRESS → "In progress". */
export function humanize(value: string): string {
  const words = value.replaceAll('_', ' ').toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
