import { FormBuilder } from '@angular/forms';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { RegisterPageComponent } from './register-page.component';

describe('RegisterPageComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let component: RegisterPageComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [FormBuilder] });
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['register', 'login', 'isAdmin']);
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    component = TestBed.runInInjectionContext(() => new RegisterPageComponent(auth, router));
  });

  function setValidForm(): void {
    component.form.setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
      password: 'password',
    });
  }

  it('validates required fields and does not submit an invalid form', () => {
    component.submit();

    expect(component.form.controls.firstName.touched).toBeTrue();
    expect(component.form.controls.email.touched).toBeTrue();
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('does not resubmit while a request is in progress', () => {
    setValidForm();
    component.loading = true;

    component.submit();

    expect(auth.register).not.toHaveBeenCalled();
  });

  it('registers, signs in, and sends a regular user to their dashboard', () => {
    setValidForm();
    auth.register.and.returnValue(of({ success: true, message: 'created' }));
    auth.login.and.returnValue(of({ accessToken: 'a', refreshToken: 'r', expiresIn: 10 }));
    auth.isAdmin.and.returnValue(false);

    component.submit();

    expect(auth.register).toHaveBeenCalledWith(component.form.getRawValue());
    expect(auth.login).toHaveBeenCalledWith('ada@example.com', 'password');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(component.loading).toBeFalse();
  });

  it('sends an administrator to the dashboard after automatic sign-in', () => {
    setValidForm();
    auth.register.and.returnValue(of({ success: true, message: 'created' }));
    auth.login.and.returnValue(of({ accessToken: 'a', refreshToken: 'r', expiresIn: 10 }));
    auth.isAdmin.and.returnValue(true);

    component.submit();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('prompts sign-in when registration succeeds but automatic sign-in fails', fakeAsync(() => {
    setValidForm();
    auth.register.and.returnValue(of({ success: true, message: 'created' }));
    auth.login.and.returnValue(throwError(() => new Error('offline')));

    component.submit();

    expect(component.successMessage).toBe(
      'Your account was created, but automatic sign-in failed. Please sign in.',
    );
    expect(component.errorMessage).toBe('');
    expect(component.loading).toBeFalse();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    tick(900);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  }));

  it('shows API and fallback registration errors', () => {
    setValidForm();
    auth.register.and.returnValue(throwError(() => ({ error: { message: 'Email is taken' } })));

    component.submit();
    expect(component.errorMessage).toBe('Email is taken');
    expect(component.loading).toBeFalse();

    auth.register.and.returnValue(throwError(() => new Error('offline')));
    component.submit();
    expect(component.errorMessage).toBe('Unable to create your account. Please try again.');
  });
});
