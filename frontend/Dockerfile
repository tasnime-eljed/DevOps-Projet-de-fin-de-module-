# =====================================================================
#  Dockerfile du frontend Angular 20
#    1) "build"   : Node.js compile l'application Angular (configuration production)
#    2) "runtime" : Nginx (non-root) sert les fichiers statiques et joue le role
#                   de reverse proxy vers le backend et users-microservice
# =====================================================================

# ---------- Etape 1 : construction Angular ----------
FROM node:24-alpine AS build
WORKDIR /app

# Dependances d'abord (couche mise en cache tant que package*.json ne changent pas)
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Code source puis build de production (defaultConfiguration = production dans angular.json)
COPY . .
RUN npm run build \
 && test -f dist/DepartementEmployee/browser/index.html

# ---------- Etape 2 : Nginx ----------
# Image officielle NGINX Inc. qui s'execute sans les droits root (port interne 8088)
FROM nginxinc/nginx-unprivileged:stable-alpine

# Configuration : fichiers Angular + routage SPA + reverse proxy des API
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Dossier de sortie reel d'Angular (builder @angular/build:application, projet DepartementEmployee)
COPY --from=build /app/dist/DepartementEmployee/browser /usr/share/nginx/html

EXPOSE 8088
