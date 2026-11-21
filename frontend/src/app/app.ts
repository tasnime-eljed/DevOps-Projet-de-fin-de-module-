import { Component, OnInit, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { Auth } from './services/auth';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly title = signal('DepartementEmployee');

  constructor(public authService: Auth, private router: Router) {}

  ngOnInit(): void {
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
