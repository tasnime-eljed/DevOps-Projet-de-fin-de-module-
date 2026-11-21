# Fiche projet DevOps — Gestion des employés (monorepo)

**Dépôt :** https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module- · **Démarrage :** `docker compose up -d --build --wait` → http://localhost:8088

## 1. Architecture

```
 Navigateur ──► frontend (Nginx :8088, seul port publié, 127.0.0.1)
                 ├─ /                       Angular 20 (fichiers statiques)
                 ├─ /departement_employee/* ─► backend (Spring Boot :8081) ──► MySQL : departement_employee
                 └─ /users/*                ─► users-microservice (:8082) ──► MySQL : users_db
                                                 (login → JWT signé, vérifié par le backend)
 db : MySQL 8.4, port non publié, volume persistant devops-projet_mysql_data
```
- Connexion par **nom de service** (`jdbc:mysql://db:3306/...`), démarrage conditionné à la santé (`depends_on: service_healthy`).
- Images non-root, `HEALTHCHECK` sur `/health`, tag explicite `ghcr.io/tasnime-eljed/devops-<service>:vX.Y.Z`.
- Secrets dans `.env` (ignoré par Git) ; en CI, `.env` aléatoire.

## 2. Chaîne CI/CD

| Étape | Déclencheur | Résultat |
|---|---|---|
| CI (`ci.yml`) | pull request / push `main` | tests Maven (Java 17 et 21), tests Angular, rapports en artefacts → **puis** (needs) images + tests de bout en bout |
| Publication | push `main` | images testées publiées `sha-<commit>` |
| Release (`release.yml`) | tag `vX.Y.Z` | même image re-taguée `vX.Y.Z` (**jamais reconstruite**) puis mise en service + contrôle de santé |
| Deploy (`deploy.yml`) | manuel | mise en service d'une version, retour arrière optionnel |

Exécution réussie : _à compléter (URL Actions)_ · Exécution en échec : _à compléter (URL Actions)_

## 3. Version mise en service

| Environnement | Version | Contrôle |
|---|---|---|
| Test (PC local) | _à compléter (ex. v1.1.0)_ | `curl.exe http://localhost:8088/departement_employee/health` → `"version":"v1.1.0","status":"UP"` |

## 4. Déroulé du retour arrière

1. Constat : la version en service (ex. `v1.1.0`) pose problème (`/health` en échec ou défaut fonctionnel).
2. Commande : `bash scripts/rollback.sh` (ou `bash scripts/rollback.sh v1.0.0`).
3. Le script relance `scripts/deploy.sh v1.0.0` : `docker compose pull` des images **déjà publiées** `v1.0.0`, `up --no-build --wait`, aucune reconstruction.
4. Contrôle automatique : les 3 routes `/health` répondent 200 **et** la version affichée est `v1.0.0` (code de sortie 0, sinon non nul).
5. Les données MySQL sont conservées (volume inchangé). La correction est ensuite livrée par une nouvelle pull request puis un nouveau tag (ex. `v1.1.1`).
