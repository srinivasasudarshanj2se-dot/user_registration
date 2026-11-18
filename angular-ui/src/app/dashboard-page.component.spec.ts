import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PageEvent } from '@angular/material/paginator';
import { of, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { DashboardPageComponent } from './dashboard-page.component';
import { UserDialogResult } from './user-dialog.component';
import { User, UserPage, UserService } from './user.service';

describe('DashboardPageComponent', () => {
  let users: jasmine.SpyObj<UserService>;
  let auth: jasmine.SpyObj<AuthService>;
  let openDialog: jasmine.Spy;
  let openSnackBar: jasmine.Spy;
  let component: DashboardPageComponent;
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
  const page: UserPage = {
    content: [user],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
  };

  beforeEach(() => {
    users = jasmine.createSpyObj<UserService>('UserService', [
      'list',
      'getMe',
      'create',
      'update',
      'delete',
    ]);
    users.list.and.returnValue(of(page));
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['logout', 'isAdmin']);
    auth.isAdmin.and.returnValue(true);
    openDialog = jasmine.createSpy('open').and.returnValue({
      afterClosed: () => of(undefined),
    } as unknown as MatDialogRef<unknown>);
    openSnackBar = jasmine.createSpy('snackBar.open');
    component = new DashboardPageComponent(
      users,
      auth,
      { open: openDialog } as unknown as MatDialog,
      { open: openSnackBar } as unknown as MatSnackBar,
    );
  });

  it('loads the initial page and reports failures', () => {
    component.ngOnInit();
    expect(users.list).toHaveBeenCalledWith(0, 10, '');
    expect(component.page).toBe(page);
    expect(component.loading).toBeFalse();

    users.list.and.returnValue(throwError(() => new Error('offline')));
    component.loadUsers();
    expect(component.errorMessage).toBe(
      'We couldn’t load your people. Please check the connection and try again.',
    );
    expect(component.loading).toBeFalse();
  });

  it('shows regular users their profile on the dashboard', () => {
    auth.isAdmin.and.returnValue(false);
    users.getMe.and.returnValue(of(user));
    component = new DashboardPageComponent(
      users,
      auth,
      { open: openDialog } as unknown as MatDialog,
      { open: openSnackBar } as unknown as MatSnackBar,
    );

    component.ngOnInit();

    expect(component.isAdmin).toBeFalse();
    expect(users.getMe).toHaveBeenCalled();
    expect(component.profile).toBe(user);
    expect(users.list).not.toHaveBeenCalled();
    expect(component.loading).toBeFalse();
  });

  it('reports profile loading errors for regular users', () => {
    auth.isAdmin.and.returnValue(false);
    users.getMe.and.returnValue(throwError(() => new Error('offline')));
    component = new DashboardPageComponent(
      users,
      auth,
      { open: openDialog } as unknown as MatDialog,
      { open: openSnackBar } as unknown as MatSnackBar,
    );

    component.ngOnInit();

    expect(component.errorMessage).toBe(
      'We couldn’t load your profile. Please check the connection and try again.',
    );
    expect(component.loading).toBeFalse();
  });

  it('resets paging when searching and applies paginator changes', () => {
    component.search.setValue('  ada@example.com  ');
    component.pageIndex = 3;
    component.applySearch();
    expect(component.pageIndex).toBe(0);
    expect(users.list).toHaveBeenCalledWith(0, 10, 'ada@example.com');

    component.changePage({ pageIndex: 2, pageSize: 25 } as PageEvent);
    expect(users.list).toHaveBeenCalledWith(2, 25, 'ada@example.com');
    expect(component.pageIndex).toBe(2);
    expect(component.pageSize).toBe(25);
  });

  it('opens the editor with the optional user and ignores cancellation', () => {
    component.openEditor();
    expect(openDialog).toHaveBeenCalledWith(
      jasmine.any(Function),
      jasmine.objectContaining({ data: { user: null }, width: '500px' }),
    );
    expect(users.create).not.toHaveBeenCalled();

    component.openEditor(user);
    expect(openDialog).toHaveBeenCalledWith(
      jasmine.any(Function),
      jasmine.objectContaining({ data: { user } }),
    );
  });

  it('creates users after the add dialog closes and refreshes the list', () => {
    const request = {
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
      password: 'password',
    };
    const result: UserDialogResult = { type: 'create', request };
    openDialog.and.returnValue({
      afterClosed: () => of(result),
    } as unknown as MatDialogRef<unknown>);
    users.create.and.returnValue(of(user));

    component.openEditor();

    expect(users.create).toHaveBeenCalledWith(request);
    expect(openSnackBar).toHaveBeenCalledWith('Person added', 'Dismiss', { duration: 2800 });
    expect(users.list).toHaveBeenCalled();
  });

  it('updates users and displays API or fallback save errors', () => {
    const request = {
      firstName: 'Augusta',
      lastName: 'Lovelace',
      email: 'ada@example.com',
      mobile: '+123456789',
    };
    openDialog.and.returnValue({
      afterClosed: () => of({ type: 'update', id: user.id, request } satisfies UserDialogResult),
    } as unknown as MatDialogRef<unknown>);
    users.update.and.returnValue(of(user));

    component.openEditor(user);
    expect(users.update).toHaveBeenCalledWith(user.id, request);
    expect(openSnackBar).toHaveBeenCalledWith('Person updated', 'Dismiss', { duration: 2800 });

    users.update.and.returnValue(throwError(() => ({ error: { message: 'Rejected' } })));
    component.openEditor(user);
    expect(openSnackBar).toHaveBeenCalledWith('Rejected', 'Dismiss', { duration: 4000 });

    users.update.and.returnValue(throwError(() => new Error('offline')));
    component.openEditor(user);
    expect(openSnackBar).toHaveBeenCalledWith('Unable to save this person', 'Dismiss', {
      duration: 4000,
    });
  });

  it('confirms removals, handles pagination, reports errors and signs out', () => {
    const confirm = spyOn(window, 'confirm');
    confirm.and.returnValue(false);
    component.remove(user);
    expect(users.delete).not.toHaveBeenCalled();

    confirm.and.returnValue(true);
    component.page = page;
    component.pageIndex = 2;
    users.delete.and.returnValue(of(void 0));
    component.remove(user);
    expect(users.delete).toHaveBeenCalledWith(user.id);
    expect(component.pageIndex).toBe(1);
    expect(openSnackBar).toHaveBeenCalledWith('Person removed', 'Dismiss', { duration: 2800 });
    expect(users.list).toHaveBeenCalled();

    users.delete.and.returnValue(throwError(() => new Error('offline')));
    component.remove(user);
    expect(openSnackBar).toHaveBeenCalledWith('Unable to remove this person', 'Dismiss', {
      duration: 4000,
    });
    component.logout();
    expect(auth.logout).toHaveBeenCalled();
  });
});
