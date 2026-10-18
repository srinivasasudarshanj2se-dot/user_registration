import { Routes } from '@angular/router';

import { authGuard } from './auth.guard';

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
    title: 'People | userspace',
    canActivate: [authGuard],
    loadComponent: () => import('./dashboard-page.component').then((module) => module.DashboardPageComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'dashboard' },
];
