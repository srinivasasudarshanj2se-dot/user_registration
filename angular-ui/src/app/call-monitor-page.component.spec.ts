import {HttpErrorResponse} from '@angular/common/http';
import {fakeAsync, TestBed, tick} from '@angular/core/testing';
import {MatSnackBar} from '@angular/material/snack-bar';
import {Router} from '@angular/router';
import {of, throwError} from 'rxjs';

import {AuthService} from './auth.service';
import {CallEvent, CallEventsService} from './call-events.service';
import {CallMonitorPageComponent} from './call-monitor-page.component';

describe('CallMonitorPageComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let calls: jasmine.SpyObj<CallEventsService>;
  let router: jasmine.SpyObj<Router>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const event: CallEvent = {
    callId: 'call-1',
    callerNumber: '+1 555 010 0100',
    direction: 'INBOUND',
    status: 'IN_PROGRESS',
    agentName: 'Alex',
    startedAt: '2026-01-02T03:04:05.000Z',
    endedAt: null,
  };

  function createComponent(): CallMonitorPageComponent {
    return TestBed.runInInjectionContext(() => new CallMonitorPageComponent(
      auth,
      calls,
      snackBar,
      router,
    ));
  }

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['isAdmin']);
    auth.isAdmin.and.returnValue(true);
    calls = jasmine.createSpyObj<CallEventsService>('CallEventsService', ['recent', 'publish']);
    calls.recent.and.returnValue(of([event]));
    calls.publish.and.returnValue(of({callId: event.callId, messageId: 'message-1', message: 'queued'}));
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);
    TestBed.configureTestingModule({
      providers: [
        {provide: AuthService, useValue: auth},
        {provide: CallEventsService, useValue: calls},
        {provide: Router, useValue: router},
        {provide: MatSnackBar, useValue: snackBar},
      ],
    });
  });

  afterEach(() => TestBed.resetTestingModule());

  it('polls for calls and retries after a failed refresh', fakeAsync(() => {
    let attempts = 0;
    calls.recent.and.callFake(() => {
      attempts++;
      return attempts === 1 ? throwError(() => new Error('offline')) : of([event]);
    });
    const component = createComponent();
    component.ngOnInit();
    tick(0);
    expect(component.errorMessage).toContain('Unable to load');
    expect(component.loading).toBeFalse();

    tick(5000);
    expect(component.events).toEqual([event]);
    expect(component.errorMessage).toBe('');
    expect(calls.recent).toHaveBeenCalledTimes(2);
  }));

  it('redirects non-administrators without requesting calls', () => {
    auth.isAdmin.and.returnValue(false);
    const component = createComponent();

    component.ngOnInit();

    expect(component.isAdmin).toBeFalse();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(calls.recent).not.toHaveBeenCalled();
  });

  it('marks invalid fields and does not publish an invalid form', () => {
    const component = createComponent();

    component.submit();

    expect(component.form.controls.callId.touched).toBeTrue();
    expect(calls.publish).not.toHaveBeenCalled();
  });

  it('publishes valid call data with normalized timestamp and optional agent', () => {
    const component = createComponent();
    component.form.setValue({
      callId: ' call-2 ',
      callerNumber: ' +1 555 010 0101 ',
      direction: 'OUTBOUND',
      status: 'COMPLETED',
      agentName: '  ',
      startedAt: '2026-01-02T03:04',
    });

    component.submit();

    expect(calls.publish).toHaveBeenCalledWith({
      callId: 'call-2',
      callerNumber: '+1 555 010 0101',
      direction: 'OUTBOUND',
      status: 'COMPLETED',
      agentName: null,
      startedAt: new Date('2026-01-02T03:04').toISOString(),
      endedAt: null,
    });
    expect(snackBar.open).toHaveBeenCalledWith(
      'Call event queued; it will appear after processing.',
      'Dismiss',
      {duration: 3500},
    );
  });

  it('shows API and fallback errors when publishing fails', () => {
    const component = createComponent();
    component.form.setValue({
      callId: 'call-3',
      callerNumber: '+1 555 010 0102',
      direction: 'INBOUND',
      status: 'MISSED',
      agentName: 'Alex',
      startedAt: '2026-01-02T03:04',
    });
    calls.publish.and.returnValue(
      throwError(() => new HttpErrorResponse({error: {message: 'Rejected'}})),
    );

    component.submit();
    expect(snackBar.open).toHaveBeenCalledWith('Rejected', 'Dismiss', {duration: 4000});

    calls.publish.and.returnValue(throwError(() => new Error('offline')));
    component.submit();
    expect(snackBar.open).toHaveBeenCalledWith('Unable to queue this call event', 'Dismiss', {
      duration: 4000,
    });
  });
});
