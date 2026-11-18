import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../environments/environment';
import { UserService } from './user.service';

describe('UserService', () => {
  let service: UserService;
  let http: HttpTestingController;
  const usersUrl = `${environment.apiUrl}/api/v1/users`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists pages and searches by email with paging parameters', () => {
    service.list(2, 20, '').subscribe();
    const listRequest = http.expectOne((request) =>
      request.url === usersUrl && request.params.get('page') === '2' && request.params.get('size') === '20',
    );
    expect(listRequest.request.method).toBe('GET');
    listRequest.flush({});

    service.list(1, 10, 'ada@example.com').subscribe();
    const searchRequest = http.expectOne(
      `${usersUrl}/search?page=1&size=10&email=ada@example.com`,
    );
    expect(searchRequest.request.method).toBe('GET');
    searchRequest.flush({});
  });

  it('supports self-service and administrator user operations', () => {
    service.getMe().subscribe();
    const getMeRequest = http.expectOne(`${usersUrl}/me`);
    expect(getMeRequest.request.method).toBe('GET');
    getMeRequest.flush({});

    const selfProfile = { firstName: 'Ada', lastName: 'Lovelace', mobile: '+123456789' };
    service.updateMe(selfProfile).subscribe();
    const updateMeRequest = http.expectOne(`${usersUrl}/me`);
    expect(updateMeRequest.request.method).toBe('PUT');
    expect(updateMeRequest.request.body).toEqual(selfProfile);
    updateMeRequest.flush({});

    const createRequest = {
      ...selfProfile,
      email: 'ada@example.com',
      password: 'password',
    };
    service.create(createRequest).subscribe();
    const createUserRequest = http.expectOne(usersUrl);
    expect(createUserRequest.request.method).toBe('POST');
    expect(createUserRequest.request.body).toEqual(createRequest);
    createUserRequest.flush({});

    const profile = { ...selfProfile, email: 'ada@example.com' };
    service.update(42, profile).subscribe();
    const updateRequest = http.expectOne(`${usersUrl}/42`);
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual(profile);
    updateRequest.flush({});

    service.delete(42).subscribe();
    const deleteRequest = http.expectOne(`${usersUrl}/42`);
    expect(deleteRequest.request.method).toBe('DELETE');
    deleteRequest.flush(null);
  });
});
