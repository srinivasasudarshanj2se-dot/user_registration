import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { UserDialogComponent, UserDialogData, UserDialogResult } from './user-dialog.component';
import { User } from './user.service';

describe('UserDialogComponent', () => {
  let close: jasmine.Spy;
  let component: UserDialogComponent;

  function createComponent(user: User | null): void {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { user } satisfies UserDialogData },
        { provide: MatDialogRef, useValue: { close } },
      ],
    });
    component = TestBed.runInInjectionContext(() => new UserDialogComponent());
  }

  function validProfile(): void {
    component.form.patchValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
    });
  }

  const user: User = {
    id: 12,
    uuid: 'uuid',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    mobile: '+123456789',
    enabled: true,
    createdAt: '2025-01-01',
    updatedAt: '2025-01-01',
  };

  it('marks invalid controls and keeps the dialog open', () => {
    createComponent(null);

    component.save();

    expect(component.form.controls.firstName.touched).toBeTrue();
    expect(component.form.controls.password.touched).toBeTrue();
    expect(close).not.toHaveBeenCalled();
  });

  it('returns a create result with the temporary password', () => {
    createComponent(null);
    validProfile();
    component.form.controls.password.setValue('password');

    component.save();

    expect(close).toHaveBeenCalledWith({
      type: 'create',
      request: {
        firstName: 'Ada',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        mobile: '+123456789',
        password: 'password',
      },
    } satisfies UserDialogResult);
  });

  it('prefills existing data and returns an update result without a password', () => {
    createComponent(user);

    expect(component.form.controls.firstName.value).toBe('Ada');
    expect(component.form.controls.password.valid).toBeTrue();
    component.form.controls.firstName.setValue('Augusta');
    component.save();

    expect(close).toHaveBeenCalledWith({
      type: 'update',
      id: user.id,
      request: {
        firstName: 'Augusta',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        mobile: '+123456789',
      },
    } satisfies UserDialogResult);
  });
});
