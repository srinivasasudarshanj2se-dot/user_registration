import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { adminGuard, authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('route guards', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  const loginTree = { path: '/login' } as unknown as ReturnType<Router['createUrlTree']>;
  const accountTree = { path: '/account' } as unknown as ReturnType<Router['createUrlTree']>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAuthenticated', 'isAdmin']);
    router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
    router.createUrlTree.and.callFake((commands) =>
      commands[0] === '/login' ? loginTree : accountTree,
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows authenticated users', () => {
    auth.isAuthenticated.and.returnValue(true);
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))).toBeTrue();
  });

  it('redirects anonymous users to login', () => {
    auth.isAuthenticated.and.returnValue(false);
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))).toBe(loginTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
  });

  it('allows administrators', () => {
    auth.isAdmin.and.returnValue(true);
    expect(TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never))).toBeTrue();
  });

  it('redirects non-administrators to their account', () => {
    auth.isAdmin.and.returnValue(false);
    expect(TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never))).toBe(accountTree);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/account']);
  });
});
