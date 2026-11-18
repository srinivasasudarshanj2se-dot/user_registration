import {Routes} from '@angular/router';

import {authGuard} from './auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in | userspace',
    loadComponent: () => import('./login-page.component').then((module) => module.LoginPageComponent),
  },
  {
    path: 'register',
    title: 'Create account | userspace',
    loadComponent: () =>
      import('./register-page.component').then((module) => module.RegisterPageComponent),
  },
  {
    path: 'dashboard',
    title: 'Dashboard | userspace',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard-page.component').then((module) => module.DashboardPageComponent),
  },
  {
    path: 'calls',
    title: 'Live calls | userspace',
    canActivate: [authGuard],
    loadComponent: () => import('./call-monitor-page.component').then((module) => module.CallMonitorPageComponent),
  },
  {
    path: 'account',
    title: 'My account | userspace',
    canActivate: [authGuard],
    loadComponent: () => import('./account-page.component').then((module) => module.AccountPageComponent),
  },
  {path: '', pathMatch: 'full', redirectTo: 'dashboard'},
  {path: '**', redirectTo: 'dashboard'},
];
