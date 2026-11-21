import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideToastr } from 'ngx-toastr';

import { VerifEmail } from './verif-email';

describe('VerifEmail', () => {
  let component: VerifEmail;
  let fixture: ComponentFixture<VerifEmail>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifEmail],
      // dépendances réelles du composant : routeur, HttpClient (simulé), notifications
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        provideToastr()
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VerifEmail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
