import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AccountPageComponent } from './account-page.component';
import { AuthService } from './auth.service';
import { SelfUserProfile, User, UserService } from './user.service';

describe('AccountPageComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let users: jasmine.SpyObj<UserService>;
  let component: AccountPageComponent;
  const user: User = {
    id: 8,
    uuid: 'uuid',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    mobile: '+123456789',
    enabled: true,
    createdAt: '2025-01-01',
    updatedAt: '2025-01-01',
  };

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout']);
    users = jasmine.createSpyObj<UserService>('UserService', ['getMe', 'updateMe']);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: UserService, useValue: users },
        { provide: Router, useValue: jasmine.createSpyObj<Router>('Router', ['navigateByUrl']) },
      ],
    });
    component = TestBed.runInInjectionContext(() => new AccountPageComponent());
  });

  it('loads the signed-in profile', () => {
    users.getMe.and.returnValue(of(user));
    component.ngOnInit();

    expect(component.form.getRawValue()).toEqual({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      mobile: user.mobile,
    });
    expect(component.loading).toBeFalse();
  });

  it('reports profile loading errors', () => {
    users.getMe.and.returnValue(throwError(() => new Error('offline')));
    component.ngOnInit();

    expect(component.errorMessage).toBe('Unable to load your profile. Please try again.');
    expect(component.loading).toBeFalse();
  });

  it('does not save invalid profiles or while loading', () => {
    component.save();
    expect(component.form.controls.firstName.touched).toBeTrue();
    expect(users.updateMe).not.toHaveBeenCalled();

    component.form.setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
    });
    component.loading = true;
    component.save();
    expect(users.updateMe).not.toHaveBeenCalled();
  });

  it('updates the profile and displays success', () => {
    const updated: User = { ...user, firstName: 'Augusta' };
    users.updateMe.and.returnValue(of(updated));
    component.form.setValue({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      mobile: user.mobile,
    });

    component.save();

    expect(users.updateMe).toHaveBeenCalledWith({
      firstName: user.firstName,
      lastName: user.lastName,
      mobile: user.mobile,
    } satisfies SelfUserProfile);
    expect(component.form.controls.firstName.value).toBe('Augusta');
    expect(component.successMessage).toBe('Your profile has been updated.');
    expect(component.loading).toBeFalse();
  });

  it('displays API or fallback errors when saving', () => {
    component.form.setValue({
      firstName: user.firstName,
      lastName: user.lastName,
      email: user.email,
      mobile: user.mobile,
    });
    users.updateMe.and.returnValue(throwError(() => ({ error: { message: 'Rejected' } })));
    component.save();
    expect(component.errorMessage).toBe('Rejected');
    expect(component.loading).toBeFalse();

    users.updateMe.and.returnValue(throwError(() => new Error('offline')));
    component.save();
    expect(component.errorMessage).toBe('Unable to save your profile. Please try again.');
  });

  it('delegates sign out to the auth service', () => {
    component.logout();

    expect(auth.logout).toHaveBeenCalled();
  });
});
