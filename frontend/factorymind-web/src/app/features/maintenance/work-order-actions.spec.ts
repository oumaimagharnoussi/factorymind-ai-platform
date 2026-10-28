import { statusActions } from './work-order-actions';

describe('statusActions', () => {
  it('offers only the moves the server allows, most useful first', () => {
    const actions = statusActions('IN_PROGRESS', ['CANCELLED', 'ON_HOLD', 'COMPLETED']);

    expect(actions.map((a) => a.status)).toEqual(['COMPLETED', 'ON_HOLD', 'CANCELLED']);
    expect(actions[0].note).toBe('required');
  });

  it('says Resume when the work order comes back from hold', () => {
    expect(statusActions('ON_HOLD', ['IN_PROGRESS', 'CANCELLED'])[0].label).toBe('Resume');
    expect(statusActions('OPEN', ['IN_PROGRESS'])[0].label).toBe('Start work');
  });

  it('offers nothing for a closed work order', () => {
    expect(statusActions('COMPLETED', [])).toEqual([]);
  });
});
