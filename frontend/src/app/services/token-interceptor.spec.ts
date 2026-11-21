import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { tokenInterceptor } from './token-interceptor';
import { Auth } from './auth';

describe('tokenInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: Auth;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([tokenInterceptor])),
        provideHttpClientTesting()
      ]
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(Auth);
    auth.token = 'jeton-de-test';
  });

  afterEach(() => controller.verify());

  it('should add the Authorization header to API calls', () => {
    http.get('/departement_employee/api').subscribe();
    const req = controller.expectOne('/departement_employee/api');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jeton-de-test');
    req.flush([]);
  });

  it('should not add the Authorization header to the login call', () => {
    http.post('/users/login', {}).subscribe();
    const req = controller.expectOne('/users/login');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush(null);
  });
});
