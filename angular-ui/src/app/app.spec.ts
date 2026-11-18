import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { appConfig } from './app.config';
import { App } from './app';

describe('App', () => {
  it('renders the router outlet', () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    });

    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('router-outlet')).not.toBeNull();
  });

  it('provides the application router and HTTP stack', () => {
    expect(appConfig.providers?.length).toBe(5);
  });
});
