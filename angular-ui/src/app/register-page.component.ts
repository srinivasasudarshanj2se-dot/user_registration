import { Component, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';

import { AuthService } from './auth.service';

@Component({
  selector: 'app-register-page',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './register-page.component.html',
  styleUrl: './auth-page.component.scss',
})
export class RegisterPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    mobile: ['', [Validators.required, Validators.pattern(/^[+0-9() -]{7,20}$/)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  submit(): void {
    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.auth.register(this.form.getRawValue()).pipe(finalize(() => (this.loading = false))).subscribe({
      next: () => {
        this.successMessage = 'Your account is ready. Sign in to continue.';
        window.setTimeout(() => void this.router.navigateByUrl('/login'), 900);
      },
      error: (error: { error?: { message?: string } }) => {
        this.errorMessage = error.error?.message ?? 'Unable to create your account. Please try again.';
      },
    });
  }
}
