import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { User } from '../model/user.model';
import { Auth } from '../services/auth';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
@Component({
  selector: 'app-register',
  imports: [FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class Register implements OnInit {

  public user = new User();
  myForm!: FormGroup;
  err: string = '';
  loading: boolean = false;   // true pendant l'envoi de l'email

  constructor(private formBuilder: FormBuilder, private authService: Auth, private router: Router ,private toastr: ToastrService) {}
  // Injection des dépendances pour le formulaire, le service d'authentification et le routeur

  ngOnInit(): void {// Initialise le formulaire d'inscription avec des validateurs
    this.myForm = this.formBuilder.group({
      username: ['', [Validators.required]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    });
  }



 onRegister(): void {
    this.user.username = this.myForm.value.username;// Récupère les valeurs du formulaire et les assigne à l'objet user
    this.user.email = this.myForm.value.email;
    this.user.password = this.myForm.value.password;

    this.err = '';
    this.loading = true;// Indique que l'envoi de l'email est en cours

    this.authService.registerUser(this.user).subscribe({// Appel du service pour enregistrer l'utilisateur
      next: () => {
        this.loading = false;
        this.authService.setRegistredUser(this.user);// Enregistre l'utilisateur pour la connexion automatique après validation de l'email
        this.toastr.success('veuillez confirmer votre email', 'Confirmation');// Affiche une notification de succès pour demander à l'utilisateur de confirmer son email
        this.router.navigate(['/verifEmail']);
      },
      error: (err: any) => {// Gestion des erreurs lors de l'inscription
        this.loading = false;
        if (err.status === 400) {
          this.err = err.error.message;
        } else {
          this.err = "Erreur lors de l'inscription";// Message d'erreur générique pour les autres erreurs
        }
      }
    });
  }
}
