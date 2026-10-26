import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../api/models';

/** Turns any HTTP failure into one sentence a maintenance engineer can act on. */
export function describeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The server cannot be reached. Check that the API gateway is running.';
    }
    const problem = error.error as ProblemDetail | null;
    if (problem?.errors) {
      return Object.entries(problem.errors)
        .map(([field, message]) => `${field}: ${message}`)
        .join(' · ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
    return `Request failed (${error.status}).`;
  }
  return 'Something went wrong.';
}
