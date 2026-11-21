#!/usr/bin/env bash
# =====================================================================
#  Mise en service d'une version publiee dans l'environnement de test
#
#    bash scripts/deploy.sh v1.0.0
#
#  1. telecharge les images ghcr.io/tasnime-eljed/devops-*:v1.0.0
#     (images publiees par la CI : elles ne sont JAMAIS reconstruites ici)
#  2. demarre la pile avec ces images (docker compose up --no-build --wait)
#  3. controle de sante : /health des 3 services + version attendue
#  4. memorise la version en service et la precedente (pour le retour arriere)
#
#  Code de sortie : 0 = version en service et saine, non nul = echec.
#  Les donnees MySQL (volume) sont conservees d'une version a l'autre.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:-}"
if ! [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage : bash scripts/deploy.sh vX.Y.Z   (ex. v1.0.0)" >&2
  exit 2
fi
[ -f .env ] || { echo "ECHEC : fichier .env absent (copier .env.example)" >&2; exit 2; }

STATE_DIR=.deploy
mkdir -p "$STATE_DIR"
CURRENT=$(cat "$STATE_DIR/current-version" 2>/dev/null || true)

export IMAGE_TAG="$VERSION"
APP_SERVICES="backend users-microservice frontend"

echo "==> Mise en service de $VERSION (version actuelle : ${CURRENT:-aucune})"

echo "==> 1/3 Telechargement des images publiees (aucune reconstruction)"
# shellcheck disable=SC2086
docker compose pull $APP_SERVICES

echo "==> 2/3 Demarrage et attente des HEALTHCHECK"
# --no-build : interdit toute reconstruction, seules les images publiees sont utilisees
docker compose up -d --no-build --wait --wait-timeout 300

echo "==> 3/3 Controle de sante et de version"
# controle sans identifiants : routes /health + version deployee
if ! env -u ADMIN_USERNAME -u ADMIN_PASSWORD EXPECTED_VERSION="$VERSION" bash scripts/smoke-test.sh; then
  echo "ECHEC : $VERSION n'est pas saine." >&2
  [ -n "$CURRENT" ] && echo "Retour arriere : bash scripts/rollback.sh   (revient a $CURRENT)" >&2
  exit 1
fi

# Memorisation pour le retour arriere (seulement si la version change)
if [ -n "$CURRENT" ] && [ "$CURRENT" != "$VERSION" ]; then
  echo "$CURRENT" > "$STATE_DIR/previous-version"
fi
echo "$VERSION" > "$STATE_DIR/current-version"
echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) $VERSION" >> "$STATE_DIR/history.log"

echo "==> $VERSION EN SERVICE : http://localhost:${FRONTEND_PORT:-8088}"
docker compose ps
