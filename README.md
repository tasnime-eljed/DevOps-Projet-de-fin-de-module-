# DevOps — Projet de fin de module

Application full-stack de **gestion des employés et des départements**, organisée en monorepo :

- un **frontend Angular 20** servi par **Nginx** ;
- un **backend Spring Boot** (employés, départements) ;
- un **microservice Spring Boot `users-microservice`** (inscription, vérification email, login JWT) ;
- une base **MySQL 8.4**.

L'ensemble se construit et démarre avec **une seule commande Docker Compose**. Une pipeline **GitHub Actions** compile, teste et vérifie les images Docker à chaque push.

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

**backend** (`/departement_employee`)

| Méthode | Endpoint | Accès |
|---|---|---|
| GET | `/api`, `/api/{id}`, `/api/employeesdep/{idDep}`, `/api/employeesByName/{nom}` | ADMIN ou USER |
| POST / PUT | `/api` | ADMIN |
| DELETE | `/api/{id}` | ADMIN |
| GET | `/dep` (Spring Data REST, format HAL) | ADMIN ou USER |
| PUT | `/dep/{id}` | ADMIN |

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

## 12. Pipeline GitHub Actions

Fichier : `.github/workflows/ci.yml`. Déclenchement : push et pull request sur `main`.

| Job | Contenu |
|---|---|
| `java` (matrice backend / users-microservice) | Java 17 + cache Maven, MySQL 8.4 en service, `mvn verify` |
| `frontend` | Node 24 + cache npm, `npm ci`, `npm run build`, tests Karma en Chrome Headless |
| `docker` | `.env` aléatoire, `docker compose config/build/up --wait`, tests de bout en bout (`scripts/smoke-test.sh`), test de persistance |

Permissions minimales (`contents: read`). **Aucune image n'est publiée et rien n'est déployé.** La CI génère ses propres secrets aléatoires : aucun GitHub Secret n'est nécessaire. Si un déploiement est ajouté plus tard, ses secrets devront être enregistrés dans *Settings → Secrets and variables → Actions*, jamais dans le YAML.

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
