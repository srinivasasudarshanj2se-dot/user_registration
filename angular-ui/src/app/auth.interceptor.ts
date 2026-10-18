import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const accessToken = auth.getAccessToken();
  const isAuthRequest = request.url.includes('/api/v1/auth/');
  const outgoing = accessToken && !isAuthRequest
    ? request.clone({ setHeaders: { Authorization: `Bearer ${accessToken}` } })
    : request;

  return next(outgoing).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isAuthRequest && accessToken) {
        auth.clearSession();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
