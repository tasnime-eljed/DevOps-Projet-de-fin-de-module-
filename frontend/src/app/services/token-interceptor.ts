import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';

// URL pour lesquelles on n'ajoute pas le header Authorization (pas encore de JWT, ou route publique)
const exclude_array: string[] = ['/login', '/register', '/verifyEmail', '/health'];

function toExclude(url: string): boolean {// Vérifie si l'URL est dans la liste des URL à exclure
  for (const path of exclude_array) {
    if (url.search(path) != -1)
      return true;
  }
  return false;
}

export const tokenInterceptor: HttpInterceptorFn = (req, next) => {// Intercepteur HTTP pour ajouter le token JWT aux requêtes sortantes
  const authService = inject(Auth);

  const jwt = authService.getToken();
  // pas de jeton (utilisateur non connecté) : ne jamais envoyer "Bearer null",
  // que le backend refuserait (403) même sur une route publique
  if (jwt && !toExclude(req.url)) {
    const reqWithToken = req.clone({
      setHeaders: { Authorization: 'Bearer ' + jwt }
    });
    return next(reqWithToken);
  }

  return next(req);
};
