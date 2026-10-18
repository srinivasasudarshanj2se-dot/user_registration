import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { CreateUserRequest, User, UserProfile } from './user.service';

export interface UserDialogData {
  user: User | null;
}

export type UserDialogResult =
  | { type: 'create'; request: CreateUserRequest }
  | { type: 'update'; id: number; request: UserProfile };

@Component({
  selector: 'app-user-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>{{ data.user ? 'Edit person' : 'Add a person' }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="dialog-form">
        <div class="dialog-row">
          <mat-form-field appearance="outline">
            <mat-label>First name</mat-label>
            <input matInput formControlName="firstName" autocomplete="given-name" />
            @if (form.controls.firstName.touched && form.controls.firstName.invalid) {
              <mat-error>Enter a first name.</mat-error>
            }
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Last name</mat-label>
            <input matInput formControlName="lastName" autocomplete="family-name" />
            @if (form.controls.lastName.touched && form.controls.lastName.invalid) {
              <mat-error>Enter a last name.</mat-error>
            }
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline">
          <mat-label>Email address</mat-label>
          <input matInput type="email" formControlName="email" autocomplete="email" />
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <mat-error>Enter a valid email.</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Mobile number</mat-label>
          <input matInput type="tel" formControlName="mobile" autocomplete="tel" />
          @if (form.controls.mobile.touched && form.controls.mobile.invalid) {
            <mat-error>Enter a valid phone number.</mat-error>
          }
        </mat-form-field>
        @if (!data.user) {
          <mat-form-field appearance="outline">
            <mat-label>Temporary password</mat-label>
            <input matInput type="password" formControlName="password" autocomplete="new-password" />
            @if (form.controls.password.touched && form.controls.password.invalid) {
              <mat-error>Use 8–72 characters.</mat-error>
            }
          </mat-form-field>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button class="save-button" (click)="save()">
        {{ data.user ? 'Save changes' : 'Add person' }}
      </button>
    </mat-dialog-actions>
  `,
  styles: `
    .dialog-form { display: grid; gap: 4px; padding-top: 10px; min-width: min(430px, 75vw); }
    .dialog-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .save-button { background: #203a36 !important; color: white !important; }
    @media (max-width: 480px) {
      .dialog-form { min-width: 0; }
      .dialog-row { grid-template-columns: 1fr; gap: 0; }
    }
  `,
})
export class UserDialogComponent {
  readonly data = inject<UserDialogData>(MAT_DIALOG_DATA);
  private readonly formBuilder = inject(FormBuilder);
  readonly form = this.formBuilder.nonNullable.group({
    firstName: [this.data.user?.firstName ?? '', [Validators.required, Validators.maxLength(100)]],
    lastName: [this.data.user?.lastName ?? '', [Validators.required, Validators.maxLength(100)]],
    email: [this.data.user?.email ?? '', [Validators.required, Validators.email, Validators.maxLength(255)]],
    mobile: [this.data.user?.mobile ?? '', [Validators.required, Validators.pattern(/^[+0-9() -]{7,20}$/)]],
    password: ['', this.data.user ? [] : [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  private readonly dialogRef = inject<MatDialogRef<UserDialogComponent, UserDialogResult>>(MatDialogRef);

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { password, ...profile } = this.form.getRawValue();
    const result: UserDialogResult = this.data.user
      ? { type: 'update', id: this.data.user.id, request: profile }
      : { type: 'create', request: { ...profile, password } };
    this.dialogRef.close(result);
  }
}
