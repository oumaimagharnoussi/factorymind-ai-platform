import { MaintenancePlan, PlanSchedule, PlanState } from '../core/api/models';

/** "Every 30 days, on the calendar" / "Every 90 days after the last job". */
export function scheduleLabel(plan: Pick<MaintenancePlan, 'schedule' | 'intervalDays'>): string {
  const every = everyLabel(plan.intervalDays);
  return plan.schedule === 'FIXED' ? `${every}, on the calendar` : `${every} after the last job`;
}

/** 1 → "Every day", 7 → "Every week", 14 → "Every 2 weeks", 30 → "Every 30 days". */
export function everyLabel(days: number): string {
  if (days === 1) return 'Every day';
  if (days === 7) return 'Every week';
  if (days % 7 === 0 && days <= 28) return `Every ${days / 7} weeks`;
  return `Every ${days} days`;
}

export const SCHEDULE_HINTS: Record<PlanSchedule, string> = {
  FIXED: 'Due on fixed dates, even if the last job was late (inspections required by law or the manufacturer).',
  FLOATING: 'Counted from the day the last job was closed (lubrication, filters and other wear items).',
};

/** What the planner will do next, in plain words for the plan list. */
export function stateLabel(plan: Pick<MaintenancePlan, 'state' | 'opensOn' | 'currentJob'>, today: string): string {
  const state: PlanState = plan.state;
  switch (state) {
    case 'PAUSED':
      return 'Paused: no work order is opened';
    case 'JOB_OPEN':
      return `Waiting for ${plan.currentJob?.code ?? 'its work order'} to be closed`;
    case 'DUE':
      return 'Due: its work order opens within a minute';
    case 'SCHEDULED': {
      const days = daysBetween(today, plan.opensOn);
      return days === 1 ? 'Work order opens tomorrow' : `Work order opens in ${days} days`;
    }
  }
}

/** Whole days from one ISO date (yyyy-mm-dd) to another. */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
}

/** Today in UTC, like the planner (yyyy-mm-dd). */
export function todayUtc(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10);
}
