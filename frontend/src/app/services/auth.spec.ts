import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { Auth } from './auth';
import { User } from '../model/user.model';

describe('Auth', () => {
  let service: Auth;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(Auth);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should send the login to users-microservice through the relative URL', () => {
    service.login(new User()).subscribe();
    const req = http.expectOne('/users/login');
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('should not be admin without roles', () => {
    expect(service.isAdmin()).toBeFalse();
  });
});
