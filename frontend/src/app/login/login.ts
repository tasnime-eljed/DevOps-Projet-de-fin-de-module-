import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { User } from '../model/user.model';
import { Auth } from '../services/auth';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-login',
  imports: [FormsModule,RouterLink],
  templateUrl: './login.html',
  styles: ``
})
export class Login {

  user = new User();
  erreur = 0;
 message: string = 'Login ou mot de passe erronés...';
  constructor(private authService: Auth, private router: Router) {}

//Angular envoie la requête, attend la réponse du serveur,
// next si la requête réussit.  error si une erreur se produit.
  onLoggedin(): void { // Appel du service pour se connecter avec les identifiants de l'utilisateur
    this.authService.login(this.user).subscribe({
      next: (data) => {
        // le microservice renvoie "Bearer eyJ...", on retire le préfixe
        const jwToken = data.headers.get('Authorization')!.replace('Bearer ', '');
        this.authService.saveToken(jwToken);
        this.router.navigate(['/']);
      },
      error: (err) => {// Gestion des erreurs lors de la connexion
        this.erreur = 1;
         if (err.error?.errorCause == 'disabled')// Vérifie si l'utilisateur est désactivé
          this.message = 'Utilisateur désactivé, veuillez contacter votre administrateur';
        else
          this.message = 'Login ou mot de passe erronés...';// Affiche un message d'erreur générique pour les autres erreurs
      }
    });
  }
}
