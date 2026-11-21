import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Auth } from './services/auth';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('DepartementEmployee');

  // version en service, lue sur la route publique /health du backend (pied de page)
  protected readonly version = signal('');

  constructor(public authService: Auth, private router: Router, private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<{ version: string }>(environment.apiURL.replace(/\/api$/, '') + '/health')
      .subscribe({
        next: h => this.version.set(h.version),
        error: () => this.version.set('indisponible')
      });

    // recharge le token sauvegardé et décode le nom + les rôles
    this.authService.loadToken();

    // pas de token ou token expiré : retour à la page de login
    if (this.authService.getToken() == null || this.authService.isTokenExpired()) {
      this.router.navigate(['/login']);
    } else {
      this.authService.isloggedIn = true;
    }
  }

  onLogout(): void {
    this.authService.logout();
  }
}
