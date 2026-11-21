import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { JwtHelperService } from '@auth0/angular-jwt';
import { User } from '../model/user.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class Auth {

  apiURL: string = environment.usersURL;

  token!: string;
  public loggedUser!: string;
  public isloggedIn: boolean = false;
  public roles!: string[];

   // utilisateur qui vient de s'inscrire (gardé en mémoire pour la connexion automatique)
  public regitredUser: User = new User();

  private helper = new JwtHelperService();//Service pour décoder le JWT

  constructor(private router: Router, private http: HttpClient) {}

  // envoie le login au microservice ; observe:'response' permet de lire les en-têtes
  login(user: User) {
    return this.http.post<User>(this.apiURL + '/login', user, { observe: 'response' });
  }//Envoie username et password au microservice

  registerUser(user: User) {
  return this.http.post<User>(this.apiURL + '/register', user, { observe: 'response' });
  //Envoie username, password, email et roles au microservice a fin de créer un nouvel utilisateur
}

validateEmail(code: string) {
    return this.http.get<User>(this.apiURL + '/verifyEmail/' + code);
  }//Envoie le code de vérification au microservice pour valider l'email de l'utilisateur

  setRegistredUser(user: User): void {
    this.regitredUser = user;
  }//Enregistre l'utilisateur qui vient de s'inscrire

  getRegistredUser(): User {
    return this.regitredUser;
  }//Retourne l'utilisateur qui vient de s'inscrire


  saveToken(jwt: string): void {
    localStorage.setItem('jwt', jwt);
    this.token = jwt;
    this.isloggedIn = true;
    this.decodeJWT();
  }//Enregistre le JWT dans le navigateur

  // extrait le nom et les rôles du token
  decodeJWT(): void {
    if (this.token == undefined)
      return;
    const decodedToken = this.helper.decodeToken(this.token);
    this.roles = decodedToken.roles;
    this.loggedUser = decodedToken.sub;
  }//Lit les informations contenues dans le JWT

  loadToken(): void {
    this.token = localStorage.getItem('jwt')!;
    this.decodeJWT();
  }//Récupère le token enregistré au démarrage

  getToken(): string {
    return this.token;
  }//Retourne le token actuel

  isAdmin(): boolean {
    if (!this.roles) return false;
    return this.roles.indexOf('ADMIN') >= 0;
  }//Vérifie si l'utilisateur possède le rôle ADMIN

  isTokenExpired(): boolean {
    return this.helper.isTokenExpired(this.token);
    //Vérifie si le token a dépassé sa date d'expiration
  }

  logout(): void {
    this.loggedUser = undefined!;
    this.roles = undefined!;
    this.token = undefined!;
    this.isloggedIn = false;
    localStorage.removeItem('jwt');
    this.router.navigate(['/login']);
  }
  //Déconnecte l'utilisateur et supprime son token
}
