import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { User } from '../model/user.model';
import { Auth } from '../services/auth';

@Component({
  selector: 'app-verif-email',
  imports: [FormsModule],
  templateUrl: './verif-email.html',
  styleUrl: './verif-email.css'
})
export class VerifEmail implements OnInit {

  code: string = '';
  user: User = new User();
  err = '';

  constructor(private authService: Auth, private router: Router) {}

  ngOnInit(): void {// Récupère l'utilisateur qui vient de s'inscrire pour la connexion automatique après validation de l'email
    // utilisateur saisi dans le formulaire d'inscription
    this.user = this.authService.getRegistredUser();
  }

  onValidateEmail(): void {// Appel du service pour valider l'email avec le code saisi
    this.authService.validateEmail(this.code).subscribe({
      next: () => {
        alert('Email validé, votre compte est activé');// Affiche un message de succès

        // connexion automatique avec les identifiants de l'inscription
        this.authService.login(this.user).subscribe({// Appel du service pour se connecter avec les identifiants de l'utilisateur
          next: (data) => {
            const jwToken = data.headers.get('Authorization')!.replace('Bearer ', '');// Récupère le token JWT dans l'en-tête de la réponse
            this.authService.saveToken(jwToken);//Enregistre le token JWT dans le navigateur
            this.router.navigate(['/']);
          },
          error: () => {
            this.router.navigate(['/login']);// Redirige vers la page de connexion si la connexion automatique échoue
          }
        });
      },
      error: (err: any) => {// Gestion des erreurs lors de la validation de l'email
        if (err.error?.errorCode == 'INVALID_TOKEN')
          this.err = 'Code invalide !';
        else if (err.error?.errorCode == 'EXPIRED_TOKEN')
          this.err = 'Code a expiré !';
        else
          this.err = 'Erreur de validation';
      }
    });
  }
}
