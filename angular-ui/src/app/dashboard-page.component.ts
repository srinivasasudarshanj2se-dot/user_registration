import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthService } from './auth.service';
import { UserDialogComponent, UserDialogResult } from './user-dialog.component';
import { User, UserPage, UserService } from './user.service';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTableModule,
    RouterLink,
  ],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.scss',
})
export class DashboardPageComponent implements OnInit {
  readonly search = new FormControl('', { nonNullable: true });
  readonly columns = ['person', 'email', 'mobile', 'joined', 'actions'];
  page: UserPage | null = null;
  pageIndex = 0;
  pageSize = 10;
  loading = false;
  errorMessage = '';

  constructor(
    private readonly users: UserService,
    private readonly auth: AuthService,
    private readonly dialog: MatDialog,
    private readonly snackBar: MatSnackBar,
  ) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading = true;
    this.errorMessage = '';
    this.users.list(this.pageIndex, this.pageSize, this.search.value.trim())
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: (page) => (this.page = page),
        error: () => {
          this.errorMessage = 'We couldn’t load your people. Please check the connection and try again.';
        },
      });
  }

  applySearch(): void {
    this.pageIndex = 0;
    this.loadUsers();
  }

  changePage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadUsers();
  }

  openEditor(user: User | null = null): void {
    const dialogRef = this.dialog.open(UserDialogComponent, {
      data: { user },
      width: '500px',
      maxWidth: 'calc(100vw - 32px)',
    });
    dialogRef.afterClosed().subscribe((result: UserDialogResult | undefined) => {
      if (!result) return;
      const saveRequest = result.type === 'update'
        ? this.users.update(result.id, result.request)
        : this.users.create(result.request);
      saveRequest.subscribe({
        next: () => {
          this.snackBar.open(user ? 'Person updated' : 'Person added', 'Dismiss', { duration: 2800 });
          this.loadUsers();
        },
        error: (error: { error?: { message?: string } }) => {
          this.snackBar.open(error.error?.message ?? 'Unable to save this person', 'Dismiss', {
            duration: 4000,
          });
        },
      });
    });
  }

  remove(user: User): void {
    if (!window.confirm(`Remove ${user.firstName} ${user.lastName} from the directory?`)) return;
    this.users.delete(user.id).subscribe({
      next: () => {
        this.snackBar.open('Person removed', 'Dismiss', { duration: 2800 });
        if (this.page?.content.length === 1 && this.pageIndex > 0) this.pageIndex--;
        this.loadUsers();
      },
      error: () => this.snackBar.open('Unable to remove this person', 'Dismiss', { duration: 4000 }),
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
