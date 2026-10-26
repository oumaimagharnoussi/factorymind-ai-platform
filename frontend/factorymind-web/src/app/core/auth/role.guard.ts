import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Auth, Role } from './auth';

/** Keeps a screen for the given roles; others land on the dashboard. The API enforces the same rule. */
export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () =>
    inject(Auth).hasAnyRole(...roles) || inject(Router).createUrlTree(['/dashboard']);
