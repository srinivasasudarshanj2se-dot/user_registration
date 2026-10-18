import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../environments/environment';

export interface User {
  id: number;
  uuid: string;
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserPage {
  content: User[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface UserProfile {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
}

export interface CreateUserRequest extends UserProfile {
  password: string;
}

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly usersUrl = `${environment.apiUrl}/api/v1/users`;

  constructor(private readonly http: HttpClient) {}

  list(page: number, size: number, email: string): Observable<UserPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    const url = email
      ? `${this.usersUrl}/search?${params.set('email', email).toString()}`
      : this.usersUrl;
    return this.http.get<UserPage>(url, email ? undefined : { params });
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.http.post<User>(this.usersUrl, request);
  }

  update(id: number, request: UserProfile): Observable<User> {
    return this.http.put<User>(`${this.usersUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.usersUrl}/${id}`);
  }
}
