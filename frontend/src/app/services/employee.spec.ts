import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { EmployeeService } from './employee';

describe('EmployeeService', () => {
  let service: EmployeeService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(EmployeeService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call the backend through the relative URL (Nginx reverse proxy)', () => {
    service.listeEmployees().subscribe();
    const req = http.expectOne('/departement_employee/api');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('should list departements through the relative URL', () => {
    service.listeDepartements().subscribe();
    const req = http.expectOne('/departement_employee/dep');
    expect(req.request.method).toBe('GET');
    req.flush({ _embedded: { departements: [] } });
  });
});
