import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Auth } from './auth';

// URL pour lesquelles on n'ajoute pas le header Authorization (pas encore de JWT)
const exclude_array: string[] = ['/login', '/register', '/verifyEmail'];

function toExclude(url: string): boolean {// Vérifie si l'URL est dans la liste des URL à exclure
  for (const path of exclude_array) {
    if (url.search(path) != -1)
      return true;
  }
  return false;
}

export const tokenInterceptor: HttpInterceptorFn = (req, next) => {// Intercepteur HTTP pour ajouter le token JWT aux requêtes sortantes
  const authService = inject(Auth);

  if (!toExclude(req.url)) {
    const jwt = authService.getToken();
    const reqWithToken = req.clone({
      setHeaders: { Authorization: 'Bearer ' + jwt }
    });
    return next(reqWithToken);
  }

  return next(req);
};
