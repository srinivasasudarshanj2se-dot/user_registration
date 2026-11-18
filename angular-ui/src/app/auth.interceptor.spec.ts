import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getAccessToken', 'clearSession']);
    auth.getAccessToken.and.returnValue('access-token');
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('adds a bearer token to non-auth requests', () => {
    client.get('/api/v1/users').subscribe();
    const request = http.expectOne('/api/v1/users');

    expect(request.request.headers.get('Authorization')).toBe('Bearer access-token');
    request.flush({});
  });

  it('does not add tokens to login requests', () => {
    client.post('/api/v1/auth/login', {}).subscribe();
    const request = http.expectOne('/api/v1/auth/login');

    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });

  it('clears the session and redirects after a protected request returns 401', () => {
    let receivedError: unknown;
    client.get('/api/v1/users/me').subscribe({
      error: (error) => (receivedError = error),
    });
    http.expectOne('/api/v1/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).toEqual(jasmine.any(HttpErrorResponse));
    expect(auth.clearSession).toHaveBeenCalled();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('does not clear the session for auth requests or non-401 errors', () => {
    let receivedError: unknown;
    client.post('/api/v1/auth/login', {}).subscribe({
      error: (error) => (receivedError = error),
    });
    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(receivedError).toEqual(jasmine.any(HttpErrorResponse));
    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();

    client.get('/api/v1/users/me').subscribe({
      error: (error) => (receivedError = error),
    });
    http.expectOne('/api/v1/users/me').flush({}, { status: 500, statusText: 'Server Error' });
    expect(auth.clearSession).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('passes requests through when no access token exists', () => {
    auth.getAccessToken.and.returnValue(null);
    client.get('/api/v1/users').subscribe();
    const request = http.expectOne('/api/v1/users');

    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({});
  });
});
