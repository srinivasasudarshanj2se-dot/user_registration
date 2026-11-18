import {routes} from './app.routes';

describe('application routes', () => {
  it('declares public, protected and fallback routes', () => {
    expect(routes.map((route) => route.path)).toEqual([
      'login',
      'register',
      'dashboard',
      'calls',
      'account',
      '',
      '**',
    ]);
    expect(routes[2].canActivate?.length).toBe(1);
    expect(routes[3].canActivate?.length).toBe(1);
    expect(routes[4].canActivate?.length).toBe(1);
    expect(routes[5].redirectTo).toBe('dashboard');
    expect(routes[6].redirectTo).toBe('dashboard');
  });

  it('lazy-loads every page component', async () => {
    const components = await Promise.all(
      routes.filter((route) => route.loadComponent).map((route) => route.loadComponent!()),
    );

    expect(components.length).toBe(5);
    expect(components.every(Boolean)).toBeTrue();
  });
});
