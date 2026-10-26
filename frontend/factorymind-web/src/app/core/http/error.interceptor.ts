import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { describeError } from './problem';

/** Shows server and network errors in a snackbar, then lets the caller handle them too. */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  return next(req).pipe(
    catchError((error) => {
      snackBar.open(describeError(error), 'Dismiss', { duration: 6000 });
      return throwError(() => error);
    }),
  );
};
