import { TestBed } from '@angular/core/testing';
import { Auth } from './auth';

describe('Auth', () => {
  it('has no role before sign-in', () => {
    const auth = TestBed.inject(Auth);
    expect(auth.state()).toBe('loading');
    expect(auth.hasAnyRole('ADMIN', 'ENGINEER', 'OPERATOR')).toBe(false);
  });
});
