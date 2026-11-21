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

| Exécution | Lien |
|---|---|
| ✅ CI réussie (PR #1, 6 jobs, matrice Java 17/21) | https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module-/actions/runs/44954886091 |
| ❌ CI en échec (PR #3, régression volontaire) | https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module-/actions/runs/44955583274 — 1re erreur utile : `SecurityApiTests.routeHealthPubliqueRenvoieUp` → `Status expected:<200> but was:<403>` ; job Docker *skipped* (needs) ; PR bloquée par la protection de `main` |
| ✅ Release v1.0.0 (promotion + mise en service) | https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module-/actions/runs/44955295411 |
| ✅ Release v1.1.1 | https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module-/actions/runs/44957984486 |
| ✅ Retour arrière sur GitHub (v1.1.1 → v1.0.0) | https://github.com/tasnime-eljed/DevOps-Projet-de-fin-de-module-/actions/runs/44958718332 |

## 3. Version mise en service

| Environnement | Version | Contrôle |
|---|---|---|
| Test (PC local) | **v1.1.1** | `curl.exe http://localhost:8088/departement_employee/health` → `"version":"v1.1.1","status":"UP"` ; pied de page de l’application : « Version en service : v1.1.1 » |
| Test (GitHub, environnement `test`) | v1.0.0 → v1.1.0 → v1.1.1 | déployées automatiquement sur tag, contrôle de santé vert |

Image jamais reconstruite : le digest de `v1.0.0` est identique à celui de `sha-4137c8a` (image testée par la CI) pour les 3 services.

## 4. Déroulé du retour arrière

Cas réel vécu : en **v1.1.0**, le pied de page affichait « indisponible » (l’intercepteur envoyait `Bearer null`, le backend répondait 403).

1. Constat : la version en service (`v1.1.0`) présente un défaut.
2. Commande : `bash scripts/rollback.sh` (ou `bash scripts/rollback.sh v1.0.0`).
3. Le script relance `scripts/deploy.sh v1.0.0` : `docker compose pull` des images **déjà publiées** `v1.0.0`, `up --no-build --wait`, aucune reconstruction.
4. Contrôle automatique : les 3 routes `/health` répondent 200 **et** la version affichée est `v1.0.0` (code de sortie 0, sinon non nul).
5. Les données MySQL sont conservées (7 employés, 18 utilisateurs avant et après).
6. Correction livrée par la PR #4 puis le tag **`v1.1.1`**, mise en service avec `bash scripts/deploy.sh v1.1.1`.

Historique local (`.deploy/history.log`) : v1.0.0 → v1.1.0 → **retour arrière v1.0.0** → v1.1.1.
