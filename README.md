# DevOps — Projet de fin de module

Application full-stack de **gestion des employés et des départements**, organisée en monorepo :

- un **frontend Angular 20** servi par **Nginx** ;
- un **backend Spring Boot** (employés, départements) ;
- un **microservice Spring Boot `users-microservice`** (inscription, vérification email, login JWT) ;
- une base **MySQL 8.4**.

L'ensemble se construit et démarre avec **une seule commande Docker Compose**. Une pipeline **GitHub Actions** compile, teste et vérifie les images Docker à chaque push.
docker compose up -d --no-build --wait
---

## 1. Architecture

```
                 Navigateur
                     │  http://localhost:8088
                     ▼
        ┌──────────────────────────────┐
        │ frontend (Nginx non-root)     │  fichiers Angular + reverse proxy
        │  /                 → Angular  │
        │  /departement_employee/* ─┐   │
        │  /users/*            ─┐   │   │
        └───────────────────────┼───┼───┘
                                │   │        réseau Docker privé : devops-net
                ┌───────────────┘   └──────────────┐
                ▼                                  ▼
   ┌──────────────────────────┐      ┌──────────────────────────────┐
   │ users-microservice :8082 │      │ backend :8081                 │
   │ /users  (login → JWT)    │      │ /departement_employee         │
   └────────────┬─────────────┘      └──────────────┬────────────────┘
                │       même clé secrète JWT_SECRET  │
                ▼                                    ▼
          ┌──────────────────────────────────────────────┐
          │ db : MySQL 8.4  (bases users_db et            │
          │ departement_employee, volume mysql_data)      │
          └──────────────────────────────────────────────┘
```

**Flux d'authentification :**
1. Angular envoie `POST /users/login` → `users-microservice` vérifie le mot de passe (BCrypt) et renvoie un **JWT** dans l'en-tête `Authorization`.
2. Angular ajoute `Authorization: Bearer <jwt>` à chaque appel (intercepteur `token-interceptor.ts`).
3. Le backend vérifie la signature du jeton avec la **même** clé `JWT_SECRET` et applique les rôles (`ADMIN`, `USER`).

Grâce au reverse proxy Nginx, le navigateur ne parle qu'à une seule origine (`localhost:8088`) : pas de problème CORS, et seuls le port 8088 est publié sur la machine.

## 2. Technologies

| Partie | Technologies |
|---|---|
| Frontend | Angular 20, TypeScript 5.9, Bootstrap 5.3, ngx-toastr, @auth0/angular-jwt, Nginx |
| Backend | Java 17, Spring Boot 4.1.1, Spring Data JPA / REST, Spring Security, JWT (Auth0), Lombok |
| users-microservice | Java 17, Spring Boot 4.1.2, Spring Security, JWT (Auth0), Spring Mail (Gmail), Lombok |
| Données | MySQL 8.4 |
| DevOps | Docker (multi-stage), Docker Compose, GitHub Actions |

## 3. Structure du monorepo

```
DevOps-Projet-de-fin-de-module/
├── backend/                 # API employés / départements (Spring Boot)
│   ├── Dockerfile           # build Maven multi-stage → JRE 17, utilisateur non-root
│   └── .dockerignore
├── users-microservice/      # inscription, login JWT, email (Spring Boot)
│   ├── Dockerfile
│   └── .dockerignore
├── frontend/                # application Angular
│   ├── Dockerfile           # build Node 24 → Nginx non-root
│   ├── nginx.conf           # SPA + reverse proxy des API
│   └── .dockerignore
├── mysql/init/              # script de création des bases (1er démarrage)
├── scripts/
│   ├── verify-all.ps1       # vérification complète sous Windows
│   └── smoke-test.sh        # tests de bout en bout (CI)
├── .github/workflows/ci.yml # pipeline GitHub Actions
├── ci/ci.yml                # copie de travail du workflow (doublon, peut être supprimée)
├── docker-compose.yml
├── .env.example             # modèle de configuration (sans secret)
└── README.md
```

## 4. Prérequis

- **Docker Desktop** (avec Docker Compose v2) — obligatoire pour l'exécution conteneurisée.
- Pour le développement sans Docker : **Java 17**, **Node.js 24 / npm 11**, MySQL local (ex. XAMPP).
- Git.

## 5. Configuration : le fichier `.env`

Toute la configuration sensible est dans un fichier `.env` à la racine, **ignoré par Git**.

```powershell
Copy-Item .env.example .env
notepad .env
```

| Variable | Rôle |
|---|---|
| `MYSQL_ROOT_PASSWORD` | mot de passe root du conteneur MySQL |
| `MYSQL_APP_USER` / `MYSQL_APP_PASSWORD` | compte MySQL utilisé par les deux applications |
| `JWT_SECRET` | clé de signature JWT, **partagée** par le backend et le microservice (≥ 32 caractères aléatoires) |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | compte Gmail + **mot de passe d'application** pour envoyer le code de vérification |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | compte administrateur initial |
| `FRONTEND_PORT` *(optionnel)* | port publié du frontend (défaut `8088`) |

Règles :
- format `CLE=valeur`, sans guillemets ni espaces autour de `=` ;
- ne jamais committer `.env`, ne jamais le copier dans une image Docker (exclu par les `.dockerignore`) ;
- générer un secret aléatoire sous PowerShell :
  ```powershell
  -join ((1..64) | ForEach-Object { '{0:x}' -f (Get-Random -Maximum 16) })
  ```
- si un secret a été publié par erreur, il doit être **révoqué et remplacé**.

**Compte administrateur :** au démarrage dans Docker, `users-microservice` crée les rôles `ADMIN` et `USER` puis le compte `ADMIN_USERNAME` **uniquement s'ils n'existent pas** (mot de passe haché en BCrypt). Un compte existant n'est jamais modifié. Hors Docker, cette initialisation est désactivée (`ADMIN_INIT_ENABLED=false` par défaut).

**Inscription sans Gmail :** si `MAIL_USERNAME` est vide (ou si Gmail est injoignable, délai max 10 s), l'inscription n'est pas bloquée : le code de validation à 6 chiffres est écrit dans les logs du conteneur.
```powershell
docker compose logs users-microservice | Select-String "Code de validation"
```
Ce mode est prévu pour le développement et la démonstration locale ; en production, configurer un vrai serveur d'envoi d'emails.

## 6. Construction et démarrage avec Docker Compose

**Démarrage en une commande** (après avoir créé `.env`) : `docker compose up -d --build --wait`

```powershell
cd C:\3eme\devops_workshop\DevOps-Projet-de-fin-de-module

docker compose config --quiet          # valide le fichier et le .env
docker compose build                   # construit les 3 images
docker compose up -d --wait            # démarre et attend que tout soit "healthy"
docker compose ps                      # état des conteneurs
```

Puis ouvrir **http://localhost:8088** et se connecter avec `ADMIN_USERNAME` / `ADMIN_PASSWORD`.

Ordre de démarrage (healthchecks) : `db` *healthy* → `backend` et `users-microservice` *healthy* → `frontend`.

## 7. Ports et URLs

| Service | Port interne | Publié sur la machine | URL |
|---|---|---|---|
| frontend (Nginx) | 8088 | **127.0.0.1:8088** (ce PC uniquement) | http://localhost:8088 |
| backend | 8081 | non (via Nginx) | http://localhost:8088/departement_employee/api |
| users-microservice | 8082 | non (via Nginx) | http://localhost:8088/users/login |
| db (MySQL) | 3306 | non | accessible seulement depuis le réseau `devops-net` |

MySQL n'est volontairement pas publié : pas de conflit avec un MySQL local (XAMPP sur 3306) et pas d'exposition inutile.

## 8. Description des services et endpoints

**users-microservice** (`/users`)

| Méthode | Endpoint | Accès |
|---|---|---|
| POST | `/users/login` | public — renvoie le JWT dans `Authorization` |
| POST | `/users/register` | public — crée un compte désactivé et envoie un code par email |
| GET | `/users/verifyEmail/{code}` | public — active le compte |
| GET | `/users/all` | ADMIN |
| GET | `/users/health` | public — santé + base MySQL + version (200 / 503) |

**backend** (`/departement_employee`)

| Méthode | Endpoint | Accès |
|---|---|---|
| GET | `/api`, `/api/{id}`, `/api/employeesdep/{idDep}`, `/api/employeesByName/{nom}` | ADMIN ou USER |
| POST / PUT | `/api` | ADMIN |
| DELETE | `/api/{id}` | ADMIN |
| GET | `/dep` (Spring Data REST, format HAL) | ADMIN ou USER |
| PUT | `/dep/{id}` | ADMIN |
| GET | `/health` | public — santé + base MySQL + version (200 / 503) |

**frontend** (Nginx) : `GET /health` public — santé du serveur web.

## 9. Arrêter et relancer sans perte de données

```powershell
docker compose stop        # met en pause les conteneurs (redémarrage rapide avec: docker compose start)
docker compose down        # supprime les conteneurs et le réseau du projet, CONSERVE le volume MySQL
docker compose up -d --wait   # relance : les données MySQL sont toujours là
```

## 9 bis. Commandes Docker utiles

```powershell
docker compose logs -f backend               # suivre les logs d'un service
docker compose logs --tail 100               # derniers logs de tous les services
docker compose restart users-microservice    # redémarrer un service
docker compose up -d --build frontend        # reconstruire un service après modification
docker compose down                          # arrêter (les données MySQL sont CONSERVÉES)
```

> ⚠️ `docker compose down -v` supprime aussi le volume `devops-projet_mysql_data`, donc **toutes les données** de la base Docker. À n'utiliser qu'en connaissance de cause.

Le script `mysql/init/01-create-databases.sh` ne s'exécute qu'au **premier** démarrage (volume vide).

## 9 ter. Consulter et exporter les données de la base Docker

La base Docker (`departement_employee` + `users_db`) est **différente** du MySQL local (XAMPP/Laragon sur le port 3306). Pour voir ce que l'application Docker enregistre :

**1. phpMyAdmin (optionnel, profil `outils`)** — ne démarre pas avec `docker compose up` :
```powershell
docker compose --profile outils up -d phpmyadmin     # http://localhost:8090
docker compose --profile outils stop phpmyadmin      # l'arrêter après usage
docker compose --profile outils down                 # arrêter TOUTE la pile, phpMyAdmin compris
```
Identifiants (ouvrir le fichier `.env` : `notepad .env`) :
- **Utilisateur** = valeur de `MYSQL_APP_USER`, **Mot de passe** = valeur de `MYSQL_APP_PASSWORD` (accès aux 2 bases du projet) ;
- ou `root` avec la valeur de `MYSQL_ROOT_PASSWORD` (accès complet).

Le mot de passe n'est **jamais vide** : `root` sans mot de passe donne `Access denied ... (using password: NO)`.

**2. En ligne de commande (sans outil supplémentaire)** — la requête SQL est envoyée par `|` : pas de guillemets imbriqués, que PowerShell 5.1 casse :
```powershell
"SELECT user_id, username, email, enabled+0 AS enabled FROM users_db.user;" | docker compose exec -T db sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -t'
"SELECT * FROM departement_employee.employee;" | docker compose exec -T db sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -t'
```
(`enabled+0` affiche 1/0 : la colonne est de type BIT.)

**3. Exporter (sauvegarde SQL)** — dans `backups/`, ignoré par Git :
```powershell
New-Item -ItemType Directory -Force backups | Out-Null
docker compose exec -T db sh -c 'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysqldump -uroot --single-transaction --databases users_db > /tmp/users_db.sql'
docker compose cp db:/tmp/users_db.sql backups\users_db.sql
```
(Remplacer `users_db` par `departement_employee users_db` pour exporter les deux bases.)

## 10. Développement sans Docker

Les deux applications Spring Boot lisent automatiquement le fichier `.env` de la racine (`spring.config.import=optional:file:../.env[.properties]`). Les variables d'environnement Windows/IntelliJ restent prioritaires.

```powershell
cd backend;              .\mvnw.cmd spring-boot:run     # http://localhost:8081/departement_employee
cd users-microservice;   .\mvnw.cmd spring-boot:run     # http://localhost:8082/users
cd frontend;             npm start                       # http://localhost:4200
```

En développement, Angular utilise `environment.development.ts` (URLs `localhost:8081` / `localhost:8082`) et CORS autorise `http://localhost:4200`.

## 11. Tests

| Partie | Commande | Base de données |
|---|---|---|
| backend | `cd backend; .\mvnw.cmd verify` | MySQL de test sur le port **3307** (`departement_employee_test`) |
| users-microservice | `cd users-microservice; .\mvnw.cmd verify` | MySQL de test sur le port **3307** (`users_db_test`) |
| frontend | `cd frontend; npm run test:ci` | aucune (HTTP simulé) |

Les tests Java utilisent le profil `test` : base **isolée**, schéma recréé (`create-drop`), données créées par les tests eux-mêmes, rollback après chaque test. Le port 3306 (données réelles) n'est jamais utilisé. MySQL de test jetable :

```powershell
docker run -d --rm --name devops-verify-mysql -p 127.0.0.1:3307:3306 -e MYSQL_ROOT_PASSWORD=testroot mysql:8.4
# ... lancer les tests ...
docker stop devops-verify-mysql
```

**Vérification complète en une commande** (MySQL de test, Maven, Angular, Docker Compose, tests de bout en bout, persistance) :

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-all.ps1
```

Le bilan est écrit dans `verification-logs\derniere-verification.log` (ignoré par Git, aucun secret affiché).

## 12. Chaîne CI/CD (GitHub Actions)

### Flux de travail Git
```
branche feature/...  ──►  pull request vers main  ──►  CI (tests + images)  ──►  merge
                                                        sur main : images publiées sha-<commit>
git tag vX.Y.Z  ──►  Release : même image re-taguée vX.Y.Z  ──►  mise en service + santé
```
- Aucun push direct sur `main` : chaque changement passe par une **pull request** dont la CI doit être verte.
- Messages de commit courts et explicites ; aucun secret (`.env`, `backups/`, exports SQL sont ignorés).

### `.github/workflows/ci.yml` — pull request et push sur `main`
| Job | Contenu |
|---|---|
| `java` (matrice 2 modules × **Java 17 et 21**) | MySQL 8.4 de test, `mvn verify` ; rapport Surefire publié en **artefact** à chaque exécution |
| `frontend` | `npm ci`, build de production, tests Karma + couverture (artefact `tests-frontend-couverture`) |
| `docker` (**needs** java + frontend) | construit les 3 images `sha-<commit>`, `up --wait` (HEALTHCHECK), `scripts/smoke-test.sh`, test de persistance ; **sur main uniquement** : publie ces mêmes images sur `ghcr.io/tasnime-eljed/devops-*` |

**Matrice Java** : les applications ciblent Java 17 (`<java.version>17`). La matrice vérifie qu'elles compilent et passent leurs tests aussi sur Java 21 (LTS suivante) : une montée de version est donc possible sans casser le projet.

### `.github/workflows/release.yml` — tag `vX.Y.Z`
1. **Promotion** : `docker buildx imagetools create` ajoute le tag `vX.Y.Z` aux images `sha-<commit>` déjà testées → **même digest, aucune reconstruction**.
2. **Mise en service automatique** (`deploy.yml`, environnement GitHub `test`) : `scripts/deploy.sh vX.Y.Z`, contrôle de santé et login JWT.

### `.github/workflows/deploy.yml` — manuel (*Actions → Deploy → Run workflow*)
Paramètres `version` et `rollback` : met `version` en service puis, si `rollback` est rempli, **revient** à cette version et revérifie la santé.

Permissions minimales : `contents: read` par défaut, `packages: write` uniquement pour les jobs qui publient. Aucun GitHub Secret n'est nécessaire (`GITHUB_TOKEN` automatique, `.env` aléatoire en CI).

## 12 bis. Livrer une version et revenir en arrière

```bash
# 1. publier : le commit doit être sur main avec une CI verte
git tag v1.0.0
git push origin v1.0.0              # -> workflow Release (promotion + mise en service)

# 2. mettre en service sur ce PC (environnement de test local, données conservées)
bash scripts/deploy.sh v1.0.0

# 3. version suivante
bash scripts/deploy.sh v1.1.0

# 4. retour arrière (images déjà publiées : rien n'est reconstruit)
bash scripts/rollback.sh            # version précédente mémorisée, ou : bash scripts/rollback.sh v1.0.0
```
`deploy.sh` : `docker compose pull` des images `vX.Y.Z`, `up --no-build --wait`, puis `smoke-test.sh` vérifie les 3 routes `/health` **et** la version affichée. Code de sortie 0 si la version est saine, non nul sinon. Les versions en service sont mémorisées dans `.deploy/` (ignoré par Git).

Version en service : `curl.exe http://localhost:8088/departement_employee/health`

## 13. Dépannage

| Problème | Cause probable / solution |
|---|---|
| `required variable ... is missing a value` | variable absente du `.env` → compléter à partir de `.env.example` |
| `port is already allocated` (8088) | port déjà utilisé → choisir un port libre supérieur (ex. `FRONTEND_PORT=8089` dans `.env`) |
| `backend` ou `users-microservice` *unhealthy* | `docker compose logs backend` ; souvent identifiants MySQL modifiés après la création du volume |
| `Access denied for user` après changement de mot de passe MySQL | le volume garde les anciens identifiants ; remettre l'ancien mot de passe (ou, en acceptant la perte des données Docker, `docker compose down -v`) |
| Login OK mais API en 403/500 | `JWT_SECRET` différent entre services ou ancien jeton dans le navigateur → se déconnecter / reconnecter |
| « Login ou mot de passe erronés » avec `admin` | le mot de passe est la valeur `ADMIN_PASSWORD` du fichier `.env` (ouvrir `.env` dans un éditeur). Changer `ADMIN_PASSWORD` **après** le premier démarrage ne modifie pas le compte déjà créé dans le volume |
| Inscription sans Gmail configuré | normal en local : l'email n'est pas envoyé, le code de validation est écrit dans les logs → `docker compose logs users-microservice \| Select-String "Code de validation"` |
| Inscription : email non reçu alors que Gmail est configuré | `MAIL_PASSWORD` doit être un « mot de passe d'application » Gmail ; voir aussi le code dans les logs (commande ci-dessus) |
| `502 Bad Gateway` sur `/users` ou `/departement_employee` | le service Spring Boot démarre encore : attendre l'état *healthy* |
| Tests Java : `Communications link failure` | le MySQL de test (port 3307) n'est pas lancé |
| Docker Desktop « WSL is unresponsive » | redémarrer Docker Desktop (ou `wsl --shutdown` puis relancer) |
