import {HttpClientTestingModule, HttpTestingController} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';

import {environment} from '../environments/environment';
import {CallEvent, CallEventsService} from './call-events.service';

describe('CallEventsService', () => {
  let service: CallEventsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({imports: [HttpClientTestingModule]});
    service = TestBed.inject(CallEventsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('fetches recent events with the requested limit', () => {
    let response: CallEvent[] | undefined;
    service.recent(12).subscribe((events) => (response = events));

    const request = http.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/api/v1/calls`,
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('limit')).toBe('12');
    request.flush([]);
    expect(response).toEqual([]);
  });

  it('publishes a call event', () => {
    const event: CallEvent = {
      callId: 'call-1',
      callerNumber: '+1 555 010 0100',
      direction: 'INBOUND',
      status: 'IN_PROGRESS',
      agentName: null,
      startedAt: '2026-01-02T03:04:05.000Z',
      endedAt: null,
    };
    let accepted = false;
    service.publish(event).subscribe((response) => (accepted = response.messageId === 'message-1'));

    const request = http.expectOne(`${environment.apiUrl}/api/v1/calls`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(event);
    request.flush({callId: 'call-1', messageId: 'message-1', message: 'queued'});
    expect(accepted).toBeTrue();
  });
});
