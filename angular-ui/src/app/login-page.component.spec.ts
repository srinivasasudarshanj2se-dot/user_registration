import { FormBuilder } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { LoginPageComponent } from './login-page.component';

describe('LoginPageComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let component: LoginPageComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [FormBuilder] });
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['login', 'isAdmin']);
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    component = TestBed.runInInjectionContext(() => new LoginPageComponent(auth, router));
  });

  it('marks invalid form fields and avoids submitting', () => {
    component.submit();

    expect(component.form.controls.email.touched).toBeTrue();
    expect(component.form.controls.password.touched).toBeTrue();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('ignores a second submit while loading', () => {
    component.form.setValue({ email: 'ada@example.com', password: 'password' });
    component.loading = true;

    component.submit();

    expect(auth.login).not.toHaveBeenCalled();
    expect(component.form.controls.email.touched).toBeTrue();
  });

  it('logs in and sends administrators to the dashboard', () => {
    component.form.setValue({ email: 'ada@example.com', password: 'password' });
    auth.login.and.returnValue(of({ accessToken: 'a', refreshToken: 'r', expiresIn: 10 }));
    auth.isAdmin.and.returnValue(true);

    component.submit();

    expect(auth.login).toHaveBeenCalledWith('ada@example.com', 'password');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(component.loading).toBeFalse();
    expect(component.errorMessage).toBe('');
  });

  it('sends regular users to their dashboard', () => {
    component.form.setValue({ email: 'ada@example.com', password: 'password' });
    auth.login.and.returnValue(of({ accessToken: 'a', refreshToken: 'r', expiresIn: 10 }));
    auth.isAdmin.and.returnValue(false);

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('displays API and fallback errors and resets loading', () => {
    component.form.setValue({ email: 'ada@example.com', password: 'wrong' });
    auth.login.and.returnValue(throwError(() => ({ error: { message: 'Rejected' } })));

    component.submit();

    expect(component.errorMessage).toBe('Rejected');
    expect(component.loading).toBeFalse();

    auth.login.and.returnValue(throwError(() => new Error('offline')));
    component.submit();
    expect(component.errorMessage).toBe('Unable to sign in. Check your connection and try again.');
  });
});
