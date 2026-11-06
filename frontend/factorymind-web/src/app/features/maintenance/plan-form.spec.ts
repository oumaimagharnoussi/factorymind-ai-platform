import { FormControl, FormGroup } from '@angular/forms';
import { leadShorterThanInterval } from './plan-form';

describe('leadShorterThanInterval', () => {
  const form = (intervalDays: number, leadDays: number) =>
    new FormGroup({ intervalDays: new FormControl(intervalDays), leadDays: new FormControl(leadDays) });

  it('opens the work order before it is due, never a whole interval ahead', () => {
    expect(leadShorterThanInterval(form(30, 3))).toBeNull();
    expect(leadShorterThanInterval(form(7, 0))).toBeNull();
    expect(leadShorterThanInterval(form(7, 7))).toEqual({ leadTooLong: true });
    expect(leadShorterThanInterval(form(7, 10))).toEqual({ leadTooLong: true });
  });
});
