import { WorkOrderStatus } from '../../core/api/models';

export interface StatusAction {
  status: WorkOrderStatus;
  label: string;
  icon: string;
  /** Completing needs a description of the work; cancelling and pausing ask for a reason. */
  note: 'required' | 'optional' | 'none';
}

const ACTIONS: Record<WorkOrderStatus, Omit<StatusAction, 'status'>> = {
  OPEN: { label: 'Reopen', icon: 'undo', note: 'none' },
  IN_PROGRESS: { label: 'Start work', icon: 'play_arrow', note: 'none' },
  ON_HOLD: { label: 'Put on hold', icon: 'pause', note: 'optional' },
  COMPLETED: { label: 'Complete', icon: 'task_alt', note: 'required' },
  CANCELLED: { label: 'Cancel', icon: 'block', note: 'optional' },
};

/** Buttons for the moves the server allows now, in a stable order; "Resume" when coming back from hold. */
export function statusActions(current: WorkOrderStatus, next: WorkOrderStatus[]): StatusAction[] {
  const order: WorkOrderStatus[] = ['IN_PROGRESS', 'COMPLETED', 'ON_HOLD', 'OPEN', 'CANCELLED'];
  return order
    .filter((status) => next.includes(status))
    .map((status) => ({
      status,
      ...ACTIONS[status],
      ...(status === 'IN_PROGRESS' && current === 'ON_HOLD' ? { label: 'Resume' } : {}),
    }));
}
