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
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './login-page.component.html',
  styleUrl: './auth-page.component.scss',
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  loading = false;
  errorMessage = '';

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
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).pipe(finalize(() => (this.loading = false))).subscribe({
      next: () => void this.router.navigateByUrl('/dashboard'),
      error: (error: { error?: { message?: string } }) => {
        this.errorMessage = error.error?.message ?? 'Unable to sign in. Check your connection and try again.';
      },
    });
  }
}
