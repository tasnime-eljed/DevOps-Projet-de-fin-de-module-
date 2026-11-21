# Préparation à la soutenance — Projet DevOps de fin de module

> Dossier pédagogique personnel. Objectif : comprendre **chaque partie** du projet, savoir la **démontrer** et **répondre honnêtement** aux questions du jury.
> Toutes les explications correspondent aux fichiers réellement présents dans le dépôt.

---

## 0. Où en est le projet ? (à mettre à jour avant la soutenance)

| Élément | État | Preuve |
|---|---|---|
| Backend : JAR Maven | ✅ vérifié | `BUILD SUCCESS` sur ton PC (Phase 2) |
| users-microservice : JAR Maven | ✅ vérifié | `BUILD SUCCESS` sur ton PC (Phase 2) |
| Image Docker du backend | ✅ vérifié | image `devops-backend:phase3`, démarrage `Started DepartementEmployeeApplication` |
| Images backend, microservice et frontend (Compose) | ✅ vérifié | 21/11 : `docker compose build` → les 3 images `Built` |
| Docker Compose complet (4 conteneurs) | ✅ vérifié | 21/11 : `docker compose up -d --wait` → db, backend, users-microservice, frontend `healthy` |
| Login admin + JWT, API protégées | ✅ vérifié | 21/11 : `scripts/smoke-test.sh` → 7/7 OK (403 sans jeton, jeton reçu, 200 avec jeton) |
| CRUD complet via Nginx (ADMIN) | ✅ vérifié | 21/11 : créer/modifier/supprimer département et employé → 200 ; POST sans jeton → 403 |
| Persistance MySQL | ✅ vérifié | 21/11 : `down` (sans `-v`) puis `up` → log « Le compte administrateur 'admin' existe deja », smoke test 7/7 |
| Interface Angular | ✅ vérifié | page `/login` affichée sur http://localhost:8088, aucune erreur console |
| Tests backend (17 tests) | ✅ vérifié | journal du 21/11 : `Tests run: 17, Failures: 0, Errors: 0`, `BUILD SUCCESS` |
| Tests users-microservice (6 tests) | ✅ vérifié | journal du 21/11 : `Tests run: 6, Failures: 0, Errors: 0`, `BUILD SUCCESS` |
| Frontend : build / tests | ✅ vérifié | 21/11 : `npm run build` → « Application bundle generation complete » (simple avertissement de budget) ; `npm run test:ci` → `TOTAL: 15 SUCCESS` |
| Pipeline GitHub Actions | ✅ vérifié | 21/11, run 44946303887 (commit `355a5dd`) : 4 jobs verts en 3 min 18 — Maven backend 17 tests, Maven users 6 tests, Angular 15 tests, Docker Compose + smoke test 7/7 avant et après redémarrage |
| Branches + pull requests, protection de `main` | ✅ vérifié | PR #1, #2, #4 fusionnées après CI verte ; PR #3 (régression volontaire) rouge et **BLOCKED** |
| Matrice Java 17 / 21 + rapports en artefacts | ✅ vérifié | 6 jobs verts ; artefacts `tests-backend-java17/21`, `tests-users-microservice-java17/21`, `tests-frontend-couverture` |
| Tags v1.0.0, v1.1.0, v1.1.1 : promotion + mise en service | ✅ vérifié | workflow Release vert pour les 3 tags ; digest `v1.0.0` = digest `sha-4137c8a` (jamais reconstruite) |
| Retour arrière | ✅ vérifié | PC : v1.1.0 → `scripts/rollback.sh` → v1.0.0 (données conservées) ; GitHub : run Deploy 44958718332 |
| Version en service (PC) | ✅ v1.1.1 | `/health` et pied de page « Version en service : v1.1.1 » ; détails dans `docs/FICHE.md` |

**Avant la soutenance**, lance une fois :
```powershell
cd C:\3eme\devops_workshop\DevOps-Projet-de-fin-de-module
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```
(ou au minimum `docker compose up -d --wait` puis le smoke test) pour revérifier le jour J.
Le seul ⏳ restant (GitHub Actions) passera à ✅ quand l'onglet **Actions** du dépôt affichera la pipeline en vert après le push.
Règle d'or devant le jury : **ne jamais affirmer qu'une chose fonctionne si tu ne l'as pas vue fonctionner.**

---

## A. Présentation générale

### Quel est l'objectif du projet ?
Une application web de **gestion des employés et des départements** d'une entreprise :
- consulter la liste des employés, les chercher par nom ou par département ;
- ajouter, modifier, supprimer un employé (réservé aux administrateurs) ;
- créer un compte, le valider par un code reçu par email, se connecter.

Le cœur du module n'est pas l'application elle-même, mais **la manière de la construire, de la livrer et de la faire tourner** : c'est la partie DevOps.

### Quel problème résout-il ?
Sans DevOps, lancer ce projet sur un autre PC demande : installer Java 17, Maven, Node.js, MySQL, créer les bases, configurer les mots de passe, lancer 3 applications dans le bon ordre… et le fameux « ça marche sur ma machine » arrive vite.

Avec notre travail :
- **une seule commande** (`docker compose up -d --wait`) démarre toute l'application, identique sur n'importe quel PC qui a Docker ;
- **les secrets** ne sont plus dans le code mais dans un fichier `.env` privé ;
- **chaque modification** envoyée sur GitHub est automatiquement compilée et testée.

### Pourquoi DevOps ?
DevOps = rapprocher le **Dev**eloppement (écrire le code) et les **Op**érations (faire tourner le code). Concrètement :
- **automatiser** ce qui est répétitif (build, tests, démarrage) ;
- **standardiser** l'environnement (conteneurs) ;
- **détecter les erreurs tôt** (intégration continue) ;
- **documenter** pour que n'importe qui puisse reprendre le projet.

### Objectifs techniques
1. Conteneuriser les 3 applications (Dockerfiles multi-stage, utilisateurs non-root).
2. Orchestrer 4 services avec Docker Compose (réseau privé, volume persistant, healthchecks).
3. Sécuriser la configuration (`.env`, secret JWT externalisé, `.gitignore`, `.dockerignore`).
4. Faire communiquer le frontend avec les API via un reverse proxy Nginx.
5. Écrire des tests sûrs (bases isolées) et une pipeline CI GitHub Actions.
6. Documenter (README, ce dossier).

---

## B. Architecture

### Schéma

```
                         Navigateur de l'utilisateur
                                   │
                                   │  http://localhost:8088
                                   ▼
            ┌───────────────────────────────────────────────┐
            │  frontend  (Nginx, conteneur non-root)          │
            │   • /                     → fichiers Angular    │
            │   • /departement_employee/* → backend:8081      │
            │   • /users/*              → users-microservice  │
            └───────────────┬───────────────────┬────────────┘
                            │                   │      réseau Docker privé "devops-net"
                            ▼                   ▼
          ┌──────────────────────────┐   ┌──────────────────────────────┐
          │ users-microservice :8082 │   │ backend :8081                 │
          │ inscription, login → JWT │   │ employés, départements        │
          │ (signe le jeton)         │   │ (vérifie le jeton)            │
          └────────────┬─────────────┘   └──────────────┬────────────────┘
                       │      même clé secrète JWT_SECRET │
                       ▼                                  ▼
                ┌───────────────────────────────────────────────┐
                │ db : MySQL 8.4                                 │
                │ bases users_db et departement_employee         │
                │ données stockées dans le volume mysql_data     │
                └───────────────────────────────────────────────┘
```

Seul le port **8088** est ouvert sur le PC (et seulement en local : `127.0.0.1`). MySQL et les deux API sont invisibles de l'extérieur.

### Les composants

**Frontend Angular** — l'interface que voit l'utilisateur (pages Employés, Login, Inscription…). Angular est compilé en fichiers statiques (HTML, JS, CSS) par `npm run build`. Il appelle les API avec `HttpClient`, et un **intercepteur** (`token-interceptor.ts`) ajoute automatiquement le jeton JWT à chaque requête.

**Backend Spring Boot** (`backend/`) — l'API métier : employés et départements. Il expose `/departement_employee/api` (contrôleur REST) et `/departement_employee/dep` (Spring Data REST). Il ne connaît pas les mots de passe des utilisateurs : il **vérifie seulement** la signature du JWT et lit les rôles qu'il contient.

**Microservice Users** (`users-microservice/`) — tout ce qui concerne les comptes : inscription, envoi du code de vérification par Gmail, activation, **login**. Au login, il vérifie le mot de passe (haché en BCrypt) et **fabrique un JWT** signé avec `JWT_SECRET`.
Pourquoi un microservice séparé ? Parce que l'authentification est une responsabilité distincte du métier : on peut la faire évoluer, la redémarrer ou la réutiliser sans toucher au backend.

**MySQL** — la base de données. Un seul serveur MySQL dans Docker, avec **deux bases séparées** : `users_db` (comptes, rôles, codes) et `departement_employee` (employés, départements). Les tables sont créées automatiquement par Hibernate (`ddl-auto=update`). Le serveur Docker est totalement indépendant du MySQL XAMPP du PC.

**Docker** — met chaque application dans un **conteneur** : un environnement isolé qui contient exactement ce qu'il faut (par exemple Java 17 + le JAR). Une **image** est le modèle, le **conteneur** est l'image en train de tourner.

**Docker Compose** — décrit les 4 conteneurs dans un seul fichier (`docker-compose.yml`) : quelles images, quelles variables, quel réseau, quel volume, dans quel ordre démarrer.

**Nginx** — un serveur web très léger. Il joue deux rôles :
1. servir les fichiers Angular ;
2. **reverse proxy** : il reçoit les appels `/users/...` et `/departement_employee/...` et les transmet au bon conteneur. Résultat : le navigateur ne parle qu'à une seule adresse, donc **pas de problème CORS**.

**GitHub Actions** — le robot d'intégration continue de GitHub. À chaque push ou pull request sur `main`, il compile les 3 applications, lance les tests, construit les images et démarre toute la pile pour la tester.

### Parcours d'une requête : « afficher la liste des employés »

1. L'utilisatrice ouvre `http://localhost:8088` → Nginx renvoie `index.html` et l'application Angular se charge.
2. Elle se connecte : Angular envoie `POST /users/login` avec `{username, password}`.
3. Nginx voit le préfixe `/users/` → transmet à `users-microservice:8082`.
4. Le microservice cherche l'utilisateur dans `users_db`, compare le mot de passe haché (BCrypt), puis crée un **JWT** contenant le nom et les rôles, signé avec `JWT_SECRET`. Il le renvoie dans l'en-tête `Authorization: Bearer eyJ...`.
5. Angular enregistre le jeton dans le `localStorage` du navigateur.
6. Elle ouvre la page Employés : Angular appelle `GET /departement_employee/api` ; l'intercepteur ajoute `Authorization: Bearer eyJ...`.
7. Nginx voit `/departement_employee/` → transmet à `backend:8081`.
8. Le filtre `JWTAuthorizationFilter` du backend vérifie la signature avec **le même** `JWT_SECRET`. Si elle est valide, il lit les rôles (`USER`, `ADMIN`).
9. Spring Security autorise la lecture (rôle USER ou ADMIN) → le contrôleur interroge MySQL (`departement_employee`) → renvoie la liste en JSON.
10. Angular affiche le tableau.

Sans jeton, l'étape 9 échoue : le backend répond **403 Forbidden**.

---

## C. Explication des fichiers

### 1. `backend/Dockerfile` (et `users-microservice/Dockerfile`, identique sauf le port 8082)

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build     # étape 1 : outils de compilation
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline             # télécharge les dépendances (mis en cache)
COPY src ./src
RUN mvn -B -q package -DskipTests               # fabrique le JAR

FROM eclipse-temurin:17-jre                      # étape 2 : seulement de quoi exécuter Java
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring --no-create-home spring
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar
USER spring                                      # pas de root
EXPOSE 8081
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

À retenir :
- **Multi-stage** : la première image (lourde, avec Maven) sert à compiler ; la seconde (légère, JRE) ne garde que le JAR. Image finale plus petite et sans code source.
- **`COPY pom.xml` avant `COPY src`** : Docker met chaque étape en cache. Si seul le code change, les dépendances ne sont pas re-téléchargées.
- **`USER spring`** : l'application ne tourne pas en administrateur (root) dans le conteneur → moins de dégâts possibles en cas de faille.
- **`-DskipTests`** : les tests sont lancés par la CI et le script de vérification, pas pendant la construction de l'image.
- **Aucun secret** dans le Dockerfile : tout arrive par variables d'environnement au démarrage.

### 2. `frontend/Dockerfile`

```dockerfile
FROM node:24-alpine AS build
COPY package.json package-lock.json ./
RUN npm ci                                        # installe exactement les versions du lock
COPY . .
RUN npm run build && test -f dist/DepartementEmployee/browser/index.html

FROM nginxinc/nginx-unprivileged:stable-alpine    # Nginx officiel, sans root, port 8088
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/DepartementEmployee/browser /usr/share/nginx/html
```
- Le dossier `dist/DepartementEmployee/browser` vient d'`angular.json` (nom du projet `DepartementEmployee`, builder `@angular/build:application`).
- Le `test -f` fait échouer le build si Angular n'a pas produit `index.html` là où on l'attend.

### 3. `.dockerignore` (un par application)
Liste ce que Docker **n'envoie pas** au moment du build : `target/`, `node_modules/`, `.git`, `.idea/`, et surtout **`.env`**. Avantages : build plus rapide, image plus propre, **aucun secret ne peut entrer dans une image**.

### 4. `docker-compose.yml`
Lignes clés :
```yaml
name: devops-projet                     # préfixe : devops-projet-backend-1, devops-projet_mysql_data...

services:
  db:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD manquant dans .env}
    volumes:
      - mysql_data:/var/lib/mysql                     # données persistantes
      - ./mysql/init:/docker-entrypoint-initdb.d:ro   # crée users_db au 1er démarrage
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 ..."]

  backend:
    build: { context: ./backend }
    environment:
      DB_URL: jdbc:mysql://db:3306/departement_employee?...   # "db" = nom du service
      JWT_SECRET: ${JWT_SECRET:?...}
    depends_on:
      db: { condition: service_healthy }                       # attend que MySQL soit prêt

  frontend:
    ports:
      - "127.0.0.1:${FRONTEND_PORT:-8088}:8088"               # seul port publié, local uniquement
    depends_on:
      backend: { condition: service_healthy }
      users-microservice: { condition: service_healthy }
```
- **`${VAR}`** : la valeur vient du fichier `.env`. **`${VAR:?message}`** : Compose refuse de démarrer si la variable manque.
- **`db` au lieu de `localhost`** : dans un conteneur, `localhost` désigne le conteneur lui-même. Docker fournit un DNS interne : le nom du service devient un nom de machine.
- **Healthcheck** : une commande que Docker lance régulièrement pour savoir si le service est *réellement* prêt (pas seulement démarré).
- **`depends_on` + `service_healthy`** : ordre de démarrage garanti db → APIs → frontend.
- **`restart: unless-stopped`** : redémarre automatiquement un service qui plante.
- **Réseau `devops-net`** : réseau privé où les 4 conteneurs se voient par leur nom.
- **Volume `mysql_data`** : les données survivent à `docker compose down`. Seul `down -v` les supprimerait (interdit dans ce projet).

### 5. `.env` et `.env.example`
- `.env.example` (versionné) : **modèle** avec des valeurs fictives `change_me...`, et l'explication de chaque variable.
- `.env` (ignoré par Git) : **vraies valeurs** sur ton PC : mots de passe MySQL Docker, `JWT_SECRET`, Gmail, admin.
- Règle : on ne commite jamais `.env`, on ne le montre jamais (même pendant la soutenance !).

### 6. `frontend/nginx.conf`
```nginx
server {
    listen 8088;
    root /usr/share/nginx/html;
    resolver 127.0.0.11 valid=10s;                # DNS interne de Docker
    proxy_set_header Host $http_host;              # Spring voit la même origine que le navigateur

    location /departement_employee/ {
        set $backend_upstream http://backend:8081;
        proxy_pass $backend_upstream;              # reverse proxy vers le backend
    }
    location /users/ {
        set $users_upstream http://users-microservice:8082;
        proxy_pass $users_upstream;
    }
    location / {
        try_files $uri $uri/ /index.html;          # routage Angular (F5 sur /employees)
    }
}
```
- **`try_files ... /index.html`** : sans cette ligne, recharger la page `/employees` donnerait une erreur 404, car ce fichier n'existe pas sur le disque ; c'est Angular qui gère cette route.
- **`resolver` + variable** : Nginx peut démarrer même si une API n'est pas encore prête.

### 7. `application.properties` (backend et microservice)
```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/departement_employee?...}
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASSWORD:}
server.port=8081
server.servlet.context-path=/departement_employee
jwt.secret=${JWT_SECRET}                                  # pas de valeur par défaut
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:4200}
spring.config.import=optional:file:../.env[.properties]   # lit le .env en développement
```
- **`${VAR:defaut}`** : prend la variable d'environnement, sinon la valeur par défaut. En local, la valeur par défaut pointe vers XAMPP ; dans Docker, Compose fournit `DB_URL` avec `db`.
- **`jwt.secret=${JWT_SECRET}` sans défaut** : l'application **refuse de démarrer** sans secret, plutôt que de tourner avec un secret faible.
- Le microservice ajoute la configuration Gmail (`MAIL_USERNAME`, `MAIL_PASSWORD`) et l'initialisation admin (`ADMIN_INIT_ENABLED`, `ADMIN_*`).

### 8. Les fichiers JWT
| Fichier | Rôle |
|---|---|
| `users-microservice/.../JWTAuthenticationFilter.java` | intercepte `POST /login`, vérifie identifiant/mot de passe, **crée et signe** le jeton (`HMAC256(secret)`), l'ajoute dans l'en-tête `Authorization` |
| `.../JWTAuthorizationFilter.java` (dans les deux services) | à chaque requête, lit `Authorization: Bearer ...`, **vérifie la signature**, extrait les rôles et informe Spring Security |
| `SecurityConfig.java` (deux services) | règles d'accès (GET : USER/ADMIN ; PUT/DELETE : ADMIN…), CORS, injection de `jwt.secret` |
| `SecParams.java` | constantes restantes (durée de validité de 10 jours, préfixe `Bearer `). Le secret en a été **retiré** en Phase 2 |
| `users-microservice/.../init/DataInitializer.java` | crée les rôles ADMIN/USER et le compte admin **s'ils n'existent pas**, mot de passe haché en BCrypt |
| `frontend/src/app/services/token-interceptor.ts` | ajoute le jeton à chaque appel HTTP d'Angular (sauf login/inscription) |

Un JWT = 3 parties séparées par des points : **en-tête** (algorithme), **contenu** (nom, rôles, date d'expiration), **signature**. Le contenu est lisible par tous (encodé en Base64, pas chiffré), mais **personne ne peut le modifier sans connaître la clé**, sinon la signature ne correspond plus.

### 9. Le workflow `.github/workflows/ci.yml`
```yaml
on:
  push:         { branches: [ main ] }
  pull_request: { branches: [ main ] }
permissions:
  contents: read                       # droits minimum
jobs:
  java:       # matrice backend + users-microservice, MySQL de test en "service", mvn verify
  frontend:   # npm ci, npm run build, tests Karma dans Chrome Headless
  docker:     # needs: [java, frontend] → .env aléatoire, compose build/up --wait,
              # smoke-test.sh, test de persistance (down puis up), nettoyage
```
- Un **job** = une suite d'étapes sur une machine virtuelle neuve fournie par GitHub.
- **Matrice** : le même job est lancé deux fois (backend, puis microservice).
- **`services: mysql`** : GitHub démarre un MySQL jetable pour les tests.
- **Cache** Maven et npm : les dépendances ne sont pas re-téléchargées à chaque fois.
- **Aucune publication** d'image, aucun déploiement. Aucun GitHub Secret nécessaire : la CI génère ses propres secrets aléatoires.

### 10. Les scripts et les tests
| Fichier | Rôle |
|---|---|
| `scripts/verify-all.ps1` | vérification complète sous Windows : outils, `.env` (sans afficher les valeurs), MySQL de test (port 3307), `mvnw verify` × 2, `npm ci/build/test`, `docker compose build/up --wait`, tests de bout en bout, persistance. Journal dans `verification-logs/` |
| `scripts/smoke-test.sh` | tests de bout en bout pour la CI : page d'accueil, route Angular, 403 sans jeton, login, API avec jeton |
| `backend/src/test/.../DepartementEmployeeApplicationTests.java` | 13 tests du repository (recherches, tri, CRUD). Crée ses propres données, rollback |
| `backend/src/test/.../SecurityApiTests.java` | sans jeton → refusé ; jeton USER → lecture OK, ajout refusé ; jeton ADMIN → ajout OK |
| `users-microservice/src/test/.../UsersMicroserviceApplicationTests.java` | rôles et admin créés, mot de passe haché, initialisation sans doublon, login → JWT, mauvais mot de passe → 401 |
| `src/test/resources/application-test.properties` | profil `test` : base **isolée** `*_test` sur le port 3307, `create-drop` |
| `frontend/src/app/**/*.spec.ts` | tests Angular : composants créés, URLs relatives, intercepteur qui ajoute le jeton |

---

## D. Commandes à connaître

### PowerShell
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `cd C:\3eme\devops_workshop\DevOps-Projet-de-fin-de-module` | se placer dans le projet | invite de commande sur ce dossier | aucun |
| `Copy-Item .env.example .env` | créer le fichier de configuration | fichier `.env` créé | écrase un `.env` existant : vérifier avant |
| `powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1` | vérification complète | bilan `RESULTAT GLOBAL : TOUT EST OK` | long (15-25 min la 1re fois), réinstalle `node_modules` |
| `curl.exe -i http://localhost:8088/departement_employee/api` | tester une API à la main | `HTTP/1.1 403` sans jeton (normal) | aucun |

### Docker
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `docker --version` / `docker ps` | vérifier Docker, lister les conteneurs qui tournent | liste avec STATUS `Up` | aucun |
| `docker images` | lister les images | `devops-projet/backend`, etc. | aucun |
| `docker build -t devops-backend:phase3 .\backend` | construire une image | `naming to ...devops-backend:phase3` | aucun |
| `docker logs <conteneur>` | lire les logs | messages de démarrage Spring | aucun |
| `docker exec <conteneur> id` | lancer une commande dans un conteneur | `uid=...(spring)` | aucun en lecture |
| `docker volume ls` | lister les volumes | `devops-projet_mysql_data` | aucun |
| `docker rm`, `docker volume rm`, `docker system prune` | supprimer | — | ⚠️ **destructif : ne pas utiliser sans réfléchir** |

### Docker Compose
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `docker compose config --quiet` | valider le fichier + `.env` | aucun message = OK | `config` sans `--quiet` **affiche les secrets** : ne pas le faire devant le jury |
| `docker compose build` | construire les 3 images | `Built` pour chaque service | aucun |
| `docker compose up -d --wait` | démarrer et attendre les healthchecks | tous les services `Healthy` | aucun |
| `docker compose ps` | état des services | `running (healthy)` | aucun |
| `docker compose logs -f backend` | suivre les logs d'un service | logs en direct (Ctrl+C pour quitter) | aucun |
| `docker compose stop` / `start` | pause / reprise | conteneurs arrêtés / relancés | aucun |
| `docker compose down` | supprimer conteneurs et réseau du projet | volume **conservé** | faible |
| `docker compose down -v` | idem + supprime le volume | — | ⚠️ **perte de toutes les données MySQL Docker** |

### Maven
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `.\mvnw.cmd clean package -DskipTests` | compiler et produire le JAR | `BUILD SUCCESS` | supprime/recrée `target/` |
| `.\mvnw.cmd verify` | compiler + tests | `Tests run: X, Failures: 0` | nécessite le MySQL de test (port 3307) |
| `.\mvnw.cmd spring-boot:run` | lancer l'app sans Docker | `Started ...Application` | utilise XAMPP par défaut |

### npm / Angular
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `npm ci` | installer les versions exactes du `package-lock.json` | `added N packages` | supprime puis recrée `node_modules` |
| `npm start` | serveur de développement | http://localhost:4200 | aucun |
| `npm run build` | build de production | dossier `dist/DepartementEmployee/browser` | aucun |
| `npm run test:ci` | tests une fois, Chrome sans fenêtre | `TOTAL: N SUCCESS` | nécessite Chrome ou Edge |

### Git
| Commande | À quoi elle sert | Résultat attendu | Risque |
|---|---|---|---|
| `git status` | voir les fichiers modifiés / non suivis | liste des changements | aucun |
| `git diff` | voir le détail des modifications | lignes `+` / `-` | aucun |
| `git check-ignore -v .env` | vérifier qu'un fichier est ignoré | ligne citant `.gitignore` | aucun |
| `git add <fichiers>` / `git commit -m "..."` | enregistrer une version | nouveau commit | bloqué tant que `.git/index.lock` existe |
| `git push origin main` | envoyer sur GitHub (déclenche la CI) | onglet *Actions* lance le workflow | publie le code : vérifier l'absence de secrets avant |
| `git log --oneline -5` | historique | 5 derniers commits | aucun |

---

## E. Questions possibles du jury (et réponses)

### DevOps et CI/CD
1. **Qu'est-ce que DevOps ?** Une culture et un ensemble de pratiques qui rapprochent développement et exploitation, en automatisant la construction, les tests et la mise en production pour livrer plus vite et avec moins d'erreurs.
2. **Différence entre CI et CD ?** CI (*Continuous Integration*) : chaque modification est automatiquement compilée et testée. CD (*Continuous Delivery/Deployment*) : la version validée est automatiquement prête à déployer, ou déployée. Notre projet fait de la **CI** ; le CD (publication d'images, déploiement) n'est volontairement pas activé.
3. **Pourquoi automatiser les tests ?** Pour détecter une régression dès le push, et pas plusieurs jours plus tard ; et pour que ce soit la même vérification pour tout le monde.
4. **Que se passe-t-il si un test échoue dans la CI ?** Le job devient rouge, les jobs suivants (`docker`) ne s'exécutent pas, et la pull request montre l'échec. Les rapports de tests sont téléchargeables en artefact.

### Docker et conteneurs
5. **Qu'est-ce qu'un conteneur ?** Un processus isolé qui embarque son application et ses dépendances, et partage le noyau du système hôte.
6. **Différence entre conteneur et machine virtuelle ?** Une VM embarque un système d'exploitation complet (lourde, lente à démarrer) ; un conteneur partage le noyau de l'hôte (léger, démarre en secondes).
7. **Différence entre image et conteneur ?** L'image est le modèle en lecture seule ; le conteneur est une instance de l'image en train de tourner. Une image peut donner plusieurs conteneurs.
8. **Pourquoi `localhost` ne marche pas entre conteneurs ?** Chaque conteneur a sa propre interface réseau : `localhost` désigne le conteneur lui-même. On utilise le nom du service (`db`, `backend`), résolu par le DNS interne de Docker.
9. **Pourquoi un utilisateur non-root ?** Si l'application est compromise, l'attaquant n'a pas les droits administrateur dans le conteneur : on limite les dégâts (principe du moindre privilège).

### Images et Dockerfiles
10. **Qu'est-ce qu'un build multi-stage ?** Un Dockerfile avec plusieurs `FROM` : une étape pour compiler (Maven/Node), une étape finale qui ne garde que le résultat (JAR ou fichiers statiques). Image plus petite et plus sûre.
11. **Pourquoi copier `pom.xml` avant le code ?** Pour profiter du cache des couches : les dépendances ne sont re-téléchargées que si `pom.xml` change.
12. **À quoi sert `.dockerignore` ?** À exclure du contexte de build les fichiers inutiles (`target`, `node_modules`) ou sensibles (`.env`).
13. **Différence entre `CMD` et `ENTRYPOINT` ?** `ENTRYPOINT` définit la commande principale du conteneur (ici `java -jar`) ; `CMD` donne des arguments par défaut qu'on peut remplacer facilement.
14. **Pourquoi `EXPOSE 8081` ?** C'est de la documentation : le port écouté par l'application. Il ne publie rien sur le PC ; c'est `ports:` dans Compose qui publie.

### Docker Compose
15. **À quoi sert Docker Compose ?** À décrire et lancer plusieurs conteneurs liés (services, réseau, volumes, variables) avec un seul fichier et une seule commande.
16. **Que fait `depends_on` avec `condition: service_healthy` ?** Il attend que le healthcheck du service dont on dépend soit au vert avant de démarrer.
17. **Qu'est-ce qu'un healthcheck ?** Une commande lancée périodiquement dans le conteneur pour savoir s'il est réellement opérationnel (ex. `mysqladmin ping`).
18. **Comment les variables du `.env` arrivent-elles dans les conteneurs ?** Compose lit `.env`, remplace les `${VAR}` du fichier, puis les passe en variables d'environnement aux conteneurs. Spring Boot les lit via `${DB_URL}`, `${JWT_SECRET}`…
19. **Pourquoi seul le port 8088 est publié ?** Le frontend Nginx est le seul point d'entrée ; MySQL et les API restent privés (moins d'exposition, pas de conflit avec XAMPP sur 3306).

### Volumes et réseaux
20. **Que se passe-t-il pour les données si on supprime le conteneur MySQL ?** Rien : elles sont dans le volume `mysql_data`. Elles ne disparaissent qu'avec `docker compose down -v` ou `docker volume rm`.
21. **Différence entre volume et bind mount ?** Un volume est géré par Docker (`mysql_data`) ; un bind mount relie un dossier du PC (`./mysql/init`). On utilise les deux.
22. **Pourquoi le script d'init MySQL ne s'exécute qu'une fois ?** L'image MySQL n'exécute `/docker-entrypoint-initdb.d` que si le dossier de données est vide (premier démarrage du volume).
23. **À quoi sert le réseau `devops-net` ?** Les conteneurs du projet s'y voient par leur nom, isolés des autres projets Docker du PC.

### Microservices
24. **Pourquoi un microservice Users séparé ?** Séparation des responsabilités : l'authentification évolue, se déploie et se redémarre indépendamment du métier.
25. **Comment les deux services se font-ils confiance ?** Ils partagent la même clé `JWT_SECRET` : le microservice signe, le backend vérifie. Le backend n'a pas besoin d'appeler le microservice à chaque requête.
26. **Pourquoi deux bases séparées ?** Chaque service possède ses données (principe « database per service ») : aucun ne dépend du schéma de l'autre.

### Spring Boot
27. **Qu'est-ce que Spring Boot ?** Un framework Java qui configure automatiquement une application (serveur Tomcat embarqué, connexion base, sécurité) pour démarrer rapidement.
28. **À quoi sert Spring Data JPA / Hibernate ?** À manipuler la base avec des objets Java (`Employee`, `Departement`) et des interfaces (`EmployeeRepository`) au lieu d'écrire du SQL. Hibernate crée les tables (`ddl-auto=update`).
29. **Que veut dire `${DB_URL:...}` ?** Lire la variable d'environnement `DB_URL`, sinon utiliser la valeur par défaut après les deux-points.
30. **Qu'est-ce que le context path ?** Un préfixe commun à toutes les URL de l'application : `/departement_employee` pour le backend, `/users` pour le microservice. C'est aussi ce qui permet à Nginx de router selon le préfixe.

### Angular
31. **Comment Angular est-il servi en production ?** Il est compilé en fichiers statiques, puis servi par Nginx. Il n'y a pas de Node.js en production.
32. **Pourquoi des URL relatives en production ?** `/departement_employee/api` est envoyé à la même adresse que la page (localhost:8088) ; Nginx route vers la bonne API. Pas de CORS, pas d'adresse codée en dur.
33. **À quoi sert l'intercepteur HTTP ?** Ajouter automatiquement `Authorization: Bearer <jwt>` à chaque requête, sans le répéter dans chaque service.

### MySQL
34. **Pourquoi un MySQL dans Docker alors que XAMPP existe ?** Pour un environnement reproductible, avec des identifiants dédiés, et sans risque pour les données personnelles de XAMPP.
35. **Pourquoi `allowPublicKeyRetrieval=true` ?** MySQL 8 utilise par défaut l'authentification `caching_sha2_password` ; sans SSL, le pilote doit pouvoir récupérer la clé publique du serveur pour envoyer le mot de passe.

### JWT et sécurité
36. **Qu'est-ce qu'un JWT ?** Un jeton signé contenant l'identité et les rôles. Le serveur n'a pas besoin de session : il vérifie la signature à chaque requête (*stateless*).
37. **Le contenu d'un JWT est-il secret ?** Non : il est seulement encodé en Base64 et lisible par tous. Il est **infalsifiable**, pas confidentiel. On n'y met jamais de mot de passe.
38. **Comment les mots de passe sont-ils stockés ?** Hachés avec **BCrypt** (fonction à sens unique, avec sel). Même l'administrateur de la base ne peut pas les relire.
39. **Pourquoi avoir retiré le secret du code ?** Un secret dans le code finit sur GitHub : n'importe qui pourrait fabriquer un jeton ADMIN. Il est maintenant dans `.env` (non versionné) et l'application refuse de démarrer sans lui.
40. **Qu'est-ce que CORS ?** Une protection des navigateurs qui bloque les appels vers une autre origine (domaine + port). Avec Nginx, tout est sur `localhost:8088`, donc même origine. En développement (`ng serve` sur 4200), on autorise `http://localhost:4200` via `CORS_ALLOWED_ORIGINS`.
41. **Différence entre 401 et 403 ?** 401 : non authentifié (identifiants absents ou faux, ex. mauvais mot de passe au login). 403 : interdit (ex. pas de jeton valide sur une route protégée, ou rôle USER sur une action ADMIN).

### Nginx
42. **Qu'est-ce qu'un reverse proxy ?** Un serveur placé devant les applications, qui reçoit toutes les requêtes et les redirige vers le bon service interne.
43. **Pourquoi `try_files $uri /index.html` ?** Parce que les routes Angular (`/employees`) n'existent pas en tant que fichiers ; Nginx renvoie `index.html` et Angular affiche la bonne page.

### Git et GitHub
44. **À quoi sert `.gitignore` ?** À empêcher Git de suivre certains fichiers : `.env`, `target/`, `node_modules/`, `.idea/`, dumps `.sql`.
45. **Qu'est-ce qu'un monorepo ?** Un seul dépôt Git qui contient plusieurs applications (ici 3). Avantages : une seule CI, des changements cohérents entre frontend et backend.
46. **Comment vérifier qu'aucun secret n'est publié ?** `git status`, `git diff`, `git check-ignore -v .env`, et relire les fichiers avant chaque commit.

### GitHub Actions, tests et déploiement
47. **Quand le workflow se déclenche-t-il ?** À chaque push et chaque pull request vers `main`.
48. **Pourquoi les tests n'utilisent-ils pas ta base XAMPP ?** Ils tournent sur une base isolée (`*_test`, port 3307) recréée à chaque fois ; on ne risque jamais de modifier ou supprimer des données réelles.
49. **Différence entre test unitaire, d'intégration et de bout en bout ?** Unitaire : une petite partie isolée (ex. l'intercepteur Angular). Intégration : plusieurs couches avec une vraie base (tests des repositories, de la sécurité). Bout en bout : toute la pile démarrée, testée par HTTP (`smoke-test.sh`).
50. **Comment déploierais-tu en production ?** Publier les images sur un registre (ex. GitHub Container Registry) dans une étape séparée avec des permissions minimales, gérer les secrets dans GitHub Secrets ou un coffre-fort, puis déployer sur un serveur ou un cluster (Docker Compose sur une VM, ou Kubernetes), avec HTTPS devant Nginx.

---

## F. Scénario de démonstration (10 à 15 minutes)

**Préparation la veille** : lancer `verify-all.ps1` ; laisser la pile démarrée ou savoir la relancer ; ouvrir à l'avance : VS Code/IntelliJ sur le projet, un PowerShell dans le dossier, le navigateur, l'onglet *Actions* de GitHub (si le code a été poussé). **Ne jamais ouvrir le fichier `.env` à l'écran.**

| Temps | Étape | Ce que tu montres / fais |
|---|---|---|
| 0:00 – 1:30 | Présentation | objectif de l'application, problème « ça marche sur ma machine », objectifs DevOps |
| 1:30 – 3:30 | Architecture | le schéma (section B) : 4 services, réseau privé, Nginx point d'entrée unique, JWT partagé |
| 3:30 – 5:30 | Fichiers clés | `backend/Dockerfile` (multi-stage, non-root), `docker-compose.yml` (db, healthchecks, depends_on, volume), `nginx.conf` (2 `location`) |
| 5:30 – 7:30 | Lancement | `docker compose up -d --wait` puis `docker compose ps` → 4 services *healthy* |
| 7:30 – 10:00 | Application | ouvrir http://localhost:8088 → login admin → liste des employés → (ADMIN) ajouter un employé → déconnexion. *(Compte USER : s'inscrire sur /register ; sans Gmail, le code de validation se lit avec `docker compose logs users-microservice \| Select-String "Code de validation"`. Un USER voit la liste mais ne peut ni ajouter un employé, ni modifier, ni supprimer.)* |
| 10:00 – 11:30 | Test des API | `curl.exe -i http://localhost:8088/departement_employee/api` → **403** sans jeton ; dans le navigateur (F12 → Réseau) montrer l'en-tête `Authorization: Bearer ...` |
| 11:30 – 12:30 | Persistance | `docker compose down` puis `docker compose up -d --wait` → l'employé ajouté est toujours là |
| 12:30 – 14:00 | CI/CD | `.github/workflows/ci.yml` : 3 jobs, tests avec MySQL de test, build des images, tests de bout en bout ; onglet *Actions* si disponible |
| 14:00 – 15:00 | Conclusion | ce qui a été appris, limites, améliorations possibles |

**Plan B** si quelque chose ne démarre pas : montrer `docker compose logs <service>`, expliquer le diagnostic, et montrer le journal `verification-logs/derniere-verification.log` de la veille. Un diagnostic calme vaut mieux qu'une démo improvisée.

---

## G. Discours oral

> Bonjour. Je vais vous présenter mon projet DevOps de fin de module.
>
> Le point de départ, c'est une application de gestion des employés et des départements, composée de trois parties : une interface Angular, une API Spring Boot pour les employés et les départements, et un microservice Spring Boot qui gère les comptes utilisateurs et l'authentification.
>
> Au début, faire tourner ce projet demandait d'installer Java, Node.js et MySQL, de créer les bases à la main et de lancer les trois applications dans le bon ordre. En plus, une clé secrète était écrite directement dans le code. Mon objectif était donc triple : rendre le projet **reproductible**, **sécurisé** et **vérifié automatiquement**.
>
> J'ai commencé par un audit du projet, puis j'ai sécurisé la configuration : tous les mots de passe et la clé JWT sont maintenant dans un fichier `.env` qui n'est jamais envoyé sur GitHub, et un fichier `.env.example` sert de modèle.
>
> Ensuite, j'ai conteneurisé chaque application avec un Dockerfile multi-stage : une première étape compile, la seconde ne garde que le résultat, et l'application s'exécute sans les droits administrateur. Pour Angular, l'image finale est un serveur Nginx.
>
> Docker Compose orchestre quatre services : MySQL, le backend, le microservice et le frontend. Ils communiquent sur un réseau privé par leur nom, par exemple `db` pour la base. Les données MySQL sont dans un volume, donc elles survivent à l'arrêt des conteneurs. Grâce aux healthchecks, les services démarrent dans le bon ordre.
>
> Nginx joue le rôle de reverse proxy : le navigateur ne parle qu'à une seule adresse, localhost:8088, et Nginx redirige chaque appel vers la bonne API. C'est plus sécurisé, car seul ce port est ouvert, et cela évite les problèmes de CORS.
>
> Pour l'authentification, le microservice vérifie le mot de passe, haché avec BCrypt, et fabrique un jeton JWT signé. Le backend vérifie cette signature avec la même clé et applique les rôles : un utilisateur peut consulter, un administrateur peut aussi ajouter, modifier ou supprimer.
>
> Enfin, j'ai réécrit les tests pour qu'ils utilisent une base de données isolée et créent leurs propres données, et j'ai mis en place une pipeline GitHub Actions qui, à chaque modification, compile, teste, construit les images et démarre toute l'application pour la tester de bout en bout.
>
> Je vais maintenant vous faire une démonstration.
>
> *(démonstration)*
>
> Pour conclure, ce projet m'a appris que DevOps ne consiste pas seulement à écrire des fichiers Docker : il s'agit de rendre une application fiable, sécurisée et vérifiable par n'importe qui. Les prochaines améliorations seraient le HTTPS, la publication des images sur un registre et un déploiement automatique. Merci de votre attention, je suis prête à répondre à vos questions.

### Questions difficiles et réponses honnêtes

**« Avez-vous tout fait vous-même ? »**
> J'ai été accompagnée par un assistant IA pour structurer le travail et écrire une partie des fichiers. J'ai validé chaque étape, exécuté les builds et les tests sur mon PC, et je suis capable d'expliquer chaque fichier. *(Dire la vérité : le jury apprécie l'honnêteté et va surtout vérifier ta compréhension.)*

**« La CI a-t-elle déjà tourné ? »**
> Réponds selon la réalité : si le code a été poussé, montre l'onglet *Actions* ; sinon : « Le workflow est prêt et sa syntaxe vérifiée ; il s'exécutera au premier push sur main. J'ai vérifié localement les mêmes étapes avec mon script. »

**« Pourquoi la clé JWT n'est-elle pas changée régulièrement / pourquoi 10 jours de validité ? »**
> C'est une limite connue : la durée de 10 jours vient du code existant. En production, on réduirait la durée (par exemple 15 minutes) et on ajouterait un jeton de rafraîchissement, avec une rotation de la clé.

**« Le JWT est stocké dans le localStorage : est-ce sûr ? »**
> C'est simple mais exposé en cas de faille XSS. Une amélioration serait un cookie `HttpOnly` et `Secure`. Je l'ai gardé pour ne pas modifier le fonctionnement existant de l'application.

**« Pourquoi pas de HTTPS ? »**
> Le projet tourne en local. En production, on mettrait un certificat (par exemple Let's Encrypt) sur Nginx ou un load balancer devant.

**« Pourquoi `ddl-auto=update` ? »**
> C'est pratique en développement : Hibernate crée les tables. En production, on utiliserait un outil de migration comme Flyway ou Liquibase pour versionner le schéma.

**« Que se passe-t-il si MySQL tombe ? »**
> Le healthcheck devient rouge, `restart: unless-stopped` redémarre le conteneur, les données sont conservées dans le volume. Les API renvoient des erreurs pendant l'indisponibilité.

**« Pourquoi les tests sont-ils sautés dans le Dockerfile (`-DskipTests`) ? »**
> Pour séparer les responsabilités : l'image sert à exécuter, la CI sert à tester. Les tests ont besoin d'une base de test, que la CI fournit. Une image n'est construite dans la CI qu'après le succès des tests.

**« Qu'est-ce qui n'est pas encore parfait dans votre projet ? »**
> *(Préparer une réponse honnête à partir de la section 0 et du bilan de `verify-all.ps1`.)* Par exemple : sans mot de passe d'application Gmail, le code de validation n'est pas envoyé par email mais écrit dans les logs (mode démonstration) ; il n'y a pas de déploiement automatique ; certaines parties de l'application d'origine (gestion des erreurs, validation des formulaires) pourraient être améliorées.

---

## Mémo de dernière minute

- 4 services : **db, backend (8081), users-microservice (8082), frontend (8088)**.
- Un seul port publié : **8088**, en local.
- `localhost` dans un conteneur = le conteneur lui-même → on utilise **le nom du service**.
- **`down`** garde les données, **`down -v`** les supprime.
- JWT : le microservice **signe**, le backend **vérifie**, avec la **même clé**.
- Mots de passe : **BCrypt**. Secrets : **`.env`**, jamais dans Git.
- Tests : base **isolée** sur le port **3307**, jamais XAMPP.
- CI : **push/PR sur main** → Maven + Angular → Docker Compose + tests de bout en bout.
