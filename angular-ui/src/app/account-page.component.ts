import {Component, OnInit, inject} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {MatButtonModule} from '@angular/material/button';
import {MatCardModule} from '@angular/material/card';
import {MatFormFieldModule} from '@angular/material/form-field';
import {MatInputModule} from '@angular/material/input';
import {finalize} from 'rxjs';

import {AuthService} from './auth.service';
import {UserService} from './user.service';

@Component({
  selector: 'app-account-page',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  template: `
    <main class="account-shell">
      <header>
        <a routerLink="/account">userspace</a>
        <button mat-button (click)="logout()">Sign out</button>
      </header>
      <mat-card>
        <h1>My account</h1>
        <p>Update your personal profile.</p>
        @if (errorMessage) {
          <p class="error" role="alert">{{ errorMessage }}</p>
        }
        @if (successMessage) {
          <p class="success" role="status">{{ successMessage }}</p>
        }
        <form [formGroup]="form" (ngSubmit)="save()">
          <mat-form-field appearance="outline">
            <mat-label>First name</mat-label>
            <input matInput formControlName="firstName" autocomplete="given-name"/>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Last name</mat-label>
            <input matInput formControlName="lastName" autocomplete="family-name"/>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Email address</mat-label>
            <input matInput type="email" formControlName="email" autocomplete="email" readonly/>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Mobile number</mat-label>
            <input matInput type="tel" formControlName="mobile" autocomplete="tel"/>
          </mat-form-field>
          <button mat-flat-button type="submit" [disabled]="loading">
            {{ loading ? 'Saving…' : 'Save changes' }}
          </button>
        </form>
      </mat-card>
    </main>
  `,
  styles: `
    .account-shell {
      max-width: 720px;
      margin: 0 auto;
      padding: 24px;
    }

    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 32px;
    }

    header a {
      color: #203a36;
      font-size: 20px;
      font-weight: 700;
      text-decoration: none;
    }

    mat-card {
      padding: 28px;
    }

    form {
      display: grid;
      gap: 8px;
      margin-top: 24px;
    }

    form button {
      justify-self: start;
      background: #203a36;
      color: white;
    }

    .error {
      color: #a32121;
    }

    .success {
      color: #176b3a;
    }
  `,
})
export class AccountPageComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    mobile: ['', [Validators.required, Validators.pattern(/^[+0-9() -]{7,20}$/)]],
  });
  loading = false;
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    this.loading = true;
    this.users.getMe().pipe(finalize(() => (this.loading = false))).subscribe({
      next: (user) => this.form.patchValue(user),
      error: () => {
        this.errorMessage = 'Unable to load your profile. Please try again.';
      },
    });
  }

  save(): void {
    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    const {firstName, lastName, mobile} = this.form.getRawValue();
    this.users.updateMe({firstName, lastName, mobile})
      .pipe(finalize(() => (this.loading = false))).subscribe({
      next: (user) => {
        this.form.patchValue(user);
        this.successMessage = 'Your profile has been updated.';
      },
      error: (error: { error?: { message?: string } }) => {
        this.errorMessage = error.error?.message ?? 'Unable to save your profile. Please try again.';
      },
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
