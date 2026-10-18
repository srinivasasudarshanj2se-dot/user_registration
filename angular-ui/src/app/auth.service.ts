import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { environment } from '../environments/environment';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly accessTokenKey = 'user-registration.access-token';
  private readonly refreshTokenKey = 'user-registration.refresh-token';
  private readonly authenticated = signal(this.readAccessToken() !== null);

  readonly isAuthenticated = this.authenticated.asReadonly();

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {}

  login(email: string, password: string): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${environment.apiUrl}/api/v1/auth/login`, { email, password })
      .pipe(tap((tokens) => this.saveTokens(tokens)));
  }

  register(request: RegisterRequest): Observable<{ success: boolean; message: string }> {
    return this.http.post<{ success: boolean; message: string }>(
      `${environment.apiUrl}/api/v1/auth/register`,
      request,
    );
  }

  getAccessToken(): string | null {
    return this.readAccessToken();
  }

  logout(): void {
    this.clearSession();
    void this.router.navigateByUrl('/login');
  }

  clearSession(): void {
    sessionStorage.removeItem(this.accessTokenKey);
    sessionStorage.removeItem(this.refreshTokenKey);
    this.authenticated.set(false);
  }

  private saveTokens(tokens: TokenResponse): void {
    sessionStorage.setItem(this.accessTokenKey, tokens.accessToken);
    sessionStorage.setItem(this.refreshTokenKey, tokens.refreshToken);
    this.authenticated.set(true);
  }

  private readAccessToken(): string | null {
    return typeof sessionStorage === 'undefined'
      ? null
      : sessionStorage.getItem(this.accessTokenKey);
  }
}
