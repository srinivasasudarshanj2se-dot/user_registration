import {HttpClient, HttpParams} from '@angular/common/http';
import {Injectable} from '@angular/core';
import {Observable} from 'rxjs';

import {environment} from '../environments/environment';

export type CallDirection = 'INBOUND' | 'OUTBOUND';
export type CallStatus = 'RINGING' | 'IN_PROGRESS' | 'COMPLETED' | 'MISSED';

export interface CallEvent {
  callId: string;
  callerNumber: string;
  direction: CallDirection;
  status: CallStatus;
  agentName: string | null;
  startedAt: string;
  endedAt: string | null;
}

export interface CallAccepted {
  callId: string;
  messageId: string;
  message: string;
}

@Injectable({providedIn: 'root'})
export class CallEventsService {
  private readonly callsUrl = `${environment.apiUrl}/api/v1/calls`;

  constructor(private readonly http: HttpClient) {
  }

  recent(limit = 50): Observable<CallEvent[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<CallEvent[]>(this.callsUrl, {params});
  }

  publish(event: CallEvent): Observable<CallAccepted> {
    return this.http.post<CallAccepted>(this.callsUrl, event);
  }
}
