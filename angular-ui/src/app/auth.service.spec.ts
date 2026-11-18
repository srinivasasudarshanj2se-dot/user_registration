import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { environment } from '../environments/environment';
import { AuthService, TokenResponse } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  let router: jasmine.SpyObj<Router>;
  const accessTokenKey = 'user-registration.access-token';
  const refreshTokenKey = 'user-registration.refresh-token';

  function tokenWithRoles(roles: string[]): string {
    const payload = btoa(JSON.stringify({ roles }))
      .replace(/=/g, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');
    return `header.${payload}.signature`;
  }

  beforeEach(() => {
    sessionStorage.clear();
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    sessionStorage.clear();
  });

  it('posts login credentials and stores both returned tokens', () => {
    const tokens: TokenResponse = {
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 3600,
    };

    service.login('person@example.com', 'password').subscribe();
    const request = http.expectOne(`${environment.apiUrl}/api/v1/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'person@example.com', password: 'password' });
    request.flush(tokens);

    expect(sessionStorage.getItem(accessTokenKey)).toBe('access');
    expect(sessionStorage.getItem(refreshTokenKey)).toBe('refresh');
    expect(service.isAuthenticated()).toBeTrue();
  });

  it('registers without changing session state', () => {
    const requestBody = {
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
      password: 'password',
    };
    service.register(requestBody).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/api/v1/auth/register`);

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(requestBody);
    request.flush({ success: true, message: 'created' });
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('reads the token and checks roles from URL-safe token payloads', () => {
    const accessToken = tokenWithRoles(['ROLE_USER', 'ROLE_ADMIN']);
    sessionStorage.setItem(accessTokenKey, accessToken);

    expect(service.getAccessToken()).toBe(accessToken);
    expect(service.hasRole('ROLE_USER')).toBeTrue();
    expect(service.isAdmin()).toBeTrue();
    expect(service.hasRole('ROLE_MISSING')).toBeFalse();
  });

  it('returns false for missing, malformed and role-less tokens', () => {
    expect(service.hasRole('ROLE_ADMIN')).toBeFalse();

    sessionStorage.setItem(accessTokenKey, 'not-a-token');
    expect(service.hasRole('ROLE_ADMIN')).toBeFalse();

    sessionStorage.setItem(accessTokenKey, 'header.e30.signature');
    expect(service.hasRole('ROLE_ADMIN')).toBeFalse();
  });

  it('clears tokens and marks the session unauthenticated', () => {
    sessionStorage.setItem(accessTokenKey, 'access');
    sessionStorage.setItem(refreshTokenKey, 'refresh');
    service.clearSession();

    expect(sessionStorage.getItem(accessTokenKey)).toBeNull();
    expect(sessionStorage.getItem(refreshTokenKey)).toBeNull();
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('logs out, clears the session and navigates to login', () => {
    sessionStorage.setItem(accessTokenKey, 'access');
    service.logout();

    expect(service.isAuthenticated()).toBeFalse();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
