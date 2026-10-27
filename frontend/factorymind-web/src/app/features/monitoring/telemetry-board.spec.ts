import { SensorSnapshot } from '../../core/api/models';
import { HISTORY_POINTS, TelemetryBoard, worst } from './telemetry-board';

function snapshot(
  value: number,
  recordedAt: string,
  overrides: Partial<SensorSnapshot> = {},
): SensorSnapshot {
  return {
    assetId: 'm-104',
    sensorType: 'VIBRATION',
    value,
    unit: 'mm/s',
    status: 'NORMAL',
    recordedAt,
    statusSince: recordedAt,
    warningThreshold: 4.5,
    criticalThreshold: 7.1,
    ...overrides,
  };
}

describe('TelemetryBoard', () => {
  it('starts from the history and appends newer stream readings', () => {
    const board = TelemetryBoard.from(
      [snapshot(3.1, '2026-10-27T10:00:06Z')],
      [
        {
          assetId: 'm-104',
          sensorType: 'VIBRATION',
          value: 3.0,
          status: 'NORMAL',
          recordedAt: '2026-10-27T10:00:03Z',
        },
        {
          assetId: 'm-104',
          sensorType: 'VIBRATION',
          value: 3.1,
          status: 'NORMAL',
          recordedAt: '2026-10-27T10:00:06Z',
        },
      ],
    ).apply(snapshot(5.2, '2026-10-27T10:00:09Z', { status: 'WARNING' }));

    const [view] = board.byAsset().get('m-104')!;
    expect(view.history).toEqual([3.0, 3.1, 5.2]);
    expect(view.snapshot.status).toBe('WARNING');
    expect(board.count('WARNING')).toBe(1);
  });

  it('ignores a reading older than the one shown', () => {
    const board = TelemetryBoard.from([snapshot(3.1, '2026-10-27T10:00:06Z')], []);

    expect(board.apply(snapshot(9, '2026-10-27T10:00:00Z'))).toBe(board);
  });

  it('keeps a bounded history per sensor', () => {
    let board = TelemetryBoard.from([], []);
    for (let i = 0; i < HISTORY_POINTS + 20; i++) {
      board = board.apply(snapshot(i, new Date(Date.UTC(2026, 9, 27, 10, 0, i)).toISOString()));
    }

    expect(board.byAsset().get('m-104')![0].history.length).toBe(HISTORY_POINTS);
  });

  it('ranks critical above warning above normal', () => {
    expect(worst(['NORMAL', 'CRITICAL', 'WARNING'])).toBe('CRITICAL');
    expect(worst([])).toBe('NORMAL');
  });
});
