import { daysBetween, everyLabel, scheduleLabel, stateLabel, todayUtc } from './plan-schedule';

describe('plan schedule labels', () => {
  it('says how often a plan repeats', () => {
    expect(everyLabel(1)).toBe('Every day');
    expect(everyLabel(7)).toBe('Every week');
    expect(everyLabel(14)).toBe('Every 2 weeks');
    expect(everyLabel(30)).toBe('Every 30 days');
    expect(scheduleLabel({ schedule: 'FIXED', intervalDays: 30 })).toBe('Every 30 days, on the calendar');
    expect(scheduleLabel({ schedule: 'FLOATING', intervalDays: 90 })).toBe('Every 90 days after the last job');
  });

  it('says what the planner does next', () => {
    const today = '2026-11-06';
    expect(stateLabel({ state: 'SCHEDULED', opensOn: '2026-11-07', currentJob: null }, today)).toBe(
      'Work order opens tomorrow',
    );
    expect(stateLabel({ state: 'SCHEDULED', opensOn: '2026-11-16', currentJob: null }, today)).toBe(
      'Work order opens in 10 days',
    );
    expect(
      stateLabel(
        {
          state: 'JOB_OPEN',
          opensOn: today,
          currentJob: { id: 'x', code: 'WO-00012', status: 'OPEN', dueDate: today, overdue: false },
        },
        today,
      ),
    ).toBe('Waiting for WO-00012 to be closed');
    expect(stateLabel({ state: 'PAUSED', opensOn: today, currentJob: null }, today)).toContain('Paused');
  });

  it('counts days between ISO dates in UTC', () => {
    expect(daysBetween('2026-10-30', '2026-11-02')).toBe(3);
    expect(todayUtc(new Date('2026-11-06T23:30:00Z'))).toBe('2026-11-06');
  });
});
