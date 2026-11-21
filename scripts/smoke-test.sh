#!/usr/bin/env bash
# =====================================================================
#  Script de controle de la pile Docker Compose (via Nginx, port 8088)
#  Code de sortie : 0 si TOUS les controles reussissent, 1 sinon.
#
#  Variables :
#    ADMIN_USERNAME / ADMIN_PASSWORD  identifiants admin (jamais affiches)
#    BASE_URL          (defaut http://localhost:8088)
#    EXPECTED_VERSION  (optionnel) version attendue dans /health, ex. v1.0.0
#
#  Utilise par la CI, par scripts/deploy.sh et a la main :
#    set -a; . ./.env; set +a; bash scripts/smoke-test.sh
# =====================================================================
set -uo pipefail
BASE="${BASE_URL:-http://localhost:8088}"
FAIL=0

check() { # $1=description  $2=code attendu  $3=code obtenu
  if [ "$2" = "$3" ]; then echo "OK    $1 (HTTP $3)"; else echo "ECHEC $1 : attendu $2, obtenu $3"; FAIL=1; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

# --- Sante des 3 services (routes publiques /health) ---
check "Sante frontend (/health)"                     200 "$(code "$BASE/health")"
check "Sante backend + MySQL (/departement_employee/health)" 200 "$(code "$BASE/departement_employee/health")"
check "Sante users-microservice + MySQL (/users/health)"     200 "$(code "$BASE/users/health")"

if [ -n "${EXPECTED_VERSION:-}" ]; then
  for svc in departement_employee users; do
    v=$(curl -s "$BASE/$svc/health" | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')
    if [ "$v" = "$EXPECTED_VERSION" ]; then echo "OK    Version $svc = $v"
    else echo "ECHEC Version $svc : attendu $EXPECTED_VERSION, obtenu '${v}'"; FAIL=1; fi
  done
fi

# --- Application et securite ---
check "Frontend Angular (/)"                    200 "$(code "$BASE/")"
check "Routage Angular (/employees)"            200 "$(code "$BASE/employees")"
check "API backend sans jeton refusee"          403 "$(code "$BASE/departement_employee/api")"

if [ -n "${ADMIN_USERNAME:-}" ] && [ -n "${ADMIN_PASSWORD:-}" ]; then
  HEADERS=$(curl -s -D - -o /dev/null -X POST "$BASE/users/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}")
  TOKEN=$(printf '%s' "$HEADERS" | tr -d '\r' | sed -n 's/^[Aa]uthorization: //p')
  if [ -n "$TOKEN" ]; then echo "OK    Login admin via users-microservice (jeton JWT recu)"; else echo "ECHEC Login admin : aucun jeton recu"; FAIL=1; fi

  check "Liste employes avec jeton (backend)"      200 "$(code -H "Authorization: $TOKEN" "$BASE/departement_employee/api")"
  check "Liste departements avec jeton (backend)"  200 "$(code -H "Authorization: $TOKEN" "$BASE/departement_employee/dep")"
  check "Liste utilisateurs ADMIN (microservice)"  200 "$(code -H "Authorization: $TOKEN" "$BASE/users/all")"
else
  echo "INFO  ADMIN_USERNAME/ADMIN_PASSWORD absents : controles avec jeton ignores"
fi

if [ "$FAIL" -eq 0 ]; then echo "SMOKE TESTS : TOUS REUSSIS"; else echo "SMOKE TESTS : ECHEC"; fi
exit "$FAIL"
