import {DatePipe} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {Component, DestroyRef, OnInit, inject} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {MatProgressSpinnerModule} from '@angular/material/progress-spinner';
import {MatSelectModule} from '@angular/material/select';
import {MatSnackBar, MatSnackBarModule} from '@angular/material/snack-bar';
import {Router, RouterLink} from '@angular/router';
import {EMPTY, catchError, exhaustMap, tap, timer} from 'rxjs';

import {AuthService} from './auth.service';
import {CallDirection, CallEvent, CallEventsService, CallStatus} from './call-events.service';

@Component({
  selector: 'app-call-monitor-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSnackBarModule,
    RouterLink,
  ],
  template: `
    <main class="call-page">
      <header class="page-header">
        <div>
          <a routerLink="/dashboard">← Dashboard</a>
          <p class="eyebrow">CUSTOMER OPERATIONS</p>
          <h1>Live call activity</h1>
          <p class="subtitle">Call events refresh automatically every five seconds.</p>
        </div>
      </header>

      @if (!isAdmin) {
        <mat-card class="message-card" role="alert">
          Call activity is available to administrators only.
        </mat-card>
      } @else {
        <mat-card class="event-card">
          <h2>Submit a call event</h2>
          <p>Use this form to test the Pub/Sub processing pipeline.</p>
          <form [formGroup]="form" (ngSubmit)="submit()">
            <mat-form-field appearance="outline">
              <mat-label>Call ID</mat-label>
              <input matInput formControlName="callId" />
              @if (form.controls.callId.touched && form.controls.callId.invalid) {
                <mat-error>Enter a call ID.</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Caller phone</mat-label>
              <input matInput type="tel" formControlName="callerNumber" />
              @if (form.controls.callerNumber.touched && form.controls.callerNumber.invalid) {
                <mat-error>Enter a valid phone number.</mat-error>
              }
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Direction</mat-label>
              <mat-select formControlName="direction">
                <mat-option value="INBOUND">Inbound</mat-option>
                <mat-option value="OUTBOUND">Outbound</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Status</mat-label>
              <mat-select formControlName="status">
                <mat-option value="RINGING">Ringing</mat-option>
                <mat-option value="IN_PROGRESS">In progress</mat-option>
                <mat-option value="COMPLETED">Completed</mat-option>
                <mat-option value="MISSED">Missed</mat-option>
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Agent name (optional)</mat-label>
              <input matInput formControlName="agentName" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Call started</mat-label>
              <input matInput type="datetime-local" formControlName="startedAt" />
            </mat-form-field>
            <button mat-flat-button class="submit-button" type="submit">Queue call event</button>
          </form>
        </mat-card>

        <mat-card class="events-card">
          <div class="events-heading">
            <div>
              <h2>Recent calls</h2>
              <p>Events appear after the Pub/Sub consumer stores them.</p>
            </div>
            @if (loading) {
              <mat-spinner diameter="24" />
            } @else {
              <span class="refresh-indicator">Auto-refreshing · {{ events.length }} calls</span>
            }
          </div>
          @if (errorMessage) {
            <p class="error-message" role="alert">{{ errorMessage }}</p>
          }
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>CALL</th>
                  <th>CALLER</th>
                  <th>DIRECTION</th>
                  <th>STATUS</th>
                  <th>AGENT</th>
                  <th>STARTED</th>
                </tr>
              </thead>
              <tbody>
                @for (event of events; track event.callId) {
                  <tr>
                    <td>{{ event.callId }}</td>
                    <td>{{ event.callerNumber }}</td>
                    <td>{{ event.direction }}</td>
                    <td><span class="status" [class]="event.status.toLowerCase()">{{ event.status.replace('_', ' ') }}</span></td>
                    <td>{{ event.agentName || '—' }}</td>
                    <td>{{ event.startedAt | date: 'medium' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty-state" colspan="6">No call events have been processed yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </mat-card>
      }
    </main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #f6f7f4; color: #203a36; }
    .call-page { max-width: 1180px; margin: auto; padding: 38px 24px; }
    .page-header { margin-bottom: 24px; }
    .page-header a { color: #486f59; text-decoration: none; font-size: 13px; }
    .eyebrow { margin: 22px 0 6px; color: #8a948e; font-size: 10px; letter-spacing: 1.4px; font-weight: 700; }
    h1 { margin: 0; font-size: 34px; letter-spacing: -1px; }
    h2 { margin: 0; font-size: 17px; }
    .subtitle, .event-card > p, .events-heading p { color: #7a8580; font-size: 12px; }
    .event-card, .events-card, .message-card { margin-bottom: 20px; padding: 22px; border: 1px solid #e9ede7; border-radius: 12px; }
    .event-card form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 14px; align-items: start; }
    .event-card p { margin-bottom: 18px; }
    .submit-button { height: 48px; background: #203a36 !important; color: #fff !important; }
    .events-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 14px; }
    .events-heading p { margin: 5px 0 0; }
    .refresh-indicator { color: #68756e; font-size: 11px; }
    .table-wrap { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; min-width: 720px; font-size: 12px; }
    th, td { padding: 13px 10px; text-align: left; border-bottom: 1px solid #eef0ec; }
    th { color: #8d9791; font-size: 9px; letter-spacing: 1px; }
    .status { padding: 4px 8px; border-radius: 10px; background: #eef3e9; font-size: 10px; }
    .completed { background: #e9f3e7; color: #426c57; }
    .missed { background: #f8e9e7; color: #a66059; }
    .empty-state { padding: 38px 10px; text-align: center; color: #87918b; }
    .error-message { color: #a23f36; font-size: 12px; }
    @media (max-width: 600px) {
      .call-page { padding: 26px 15px; }
      .event-card form { grid-template-columns: 1fr; }
      .events-heading { align-items: flex-start; flex-direction: column; }
      h1 { font-size: 29px; }
    }
  `,
})
export class CallMonitorPageComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly formBuilder = inject(FormBuilder);
  readonly isAdmin: boolean;
  readonly form = this.formBuilder.nonNullable.group({
    callId: ['', [Validators.required, Validators.maxLength(128)]],
    callerNumber: ['', [Validators.required, Validators.pattern(/^[+0-9() .-]{7,32}$/)]],
    direction: this.formBuilder.nonNullable.control<CallDirection>('INBOUND'),
    status: this.formBuilder.nonNullable.control<CallStatus>('IN_PROGRESS'),
    agentName: ['', Validators.maxLength(100)],
    startedAt: ['', Validators.required],
  });
  events: CallEvent[] = [];
  loading = false;
  errorMessage = '';

  constructor(
    auth: AuthService,
    private readonly callEvents: CallEventsService,
    private readonly snackBar: MatSnackBar,
    private readonly router: Router,
  ) {
    this.isAdmin = auth.isAdmin();
  }

  ngOnInit(): void {
    if (!this.isAdmin) {
      void this.router.navigateByUrl('/dashboard');
      return;
    }
    timer(0, 5000)
      .pipe(
        tap(() => {
          this.loading = true;
        }),
        exhaustMap(() => this.callEvents.recent().pipe(
          tap((events) => {
            this.events = events;
            this.errorMessage = '';
            this.loading = false;
          }),
          catchError(() => {
            this.errorMessage = 'Unable to load call activity. Retrying automatically.';
            this.loading = false;
            return EMPTY;
          }),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const values = this.form.getRawValue();
    const event: CallEvent = {
      callId: values.callId.trim(),
      callerNumber: values.callerNumber.trim(),
      direction: values.direction,
      status: values.status,
      agentName: values.agentName.trim() || null,
      startedAt: new Date(values.startedAt).toISOString(),
      endedAt: null,
    };
    this.callEvents.publish(event).subscribe({
      next: () => this.snackBar.open('Call event queued; it will appear after processing.', 'Dismiss', {
        duration: 3500,
      }),
      error: (error: HttpErrorResponse) => this.snackBar.open(
        error.error?.message ?? 'Unable to queue this call event',
        'Dismiss',
        {duration: 4000},
      ),
    });
  }
}
