#!/usr/bin/env bash
# =====================================================================
#  Tests de bout en bout de la pile Docker Compose (via Nginx, port 8088)
#  Utilise par GitHub Actions. Les identifiants admin viennent des variables
#  ADMIN_USERNAME / ADMIN_PASSWORD (jamais affichees).
# =====================================================================
set -uo pipefail
BASE="${BASE_URL:-http://localhost:8088}"
FAIL=0

check() { # $1=description  $2=code attendu  $3=code obtenu
  if [ "$2" = "$3" ]; then echo "OK    $1 (HTTP $3)"; else echo "ECHEC $1 : attendu $2, obtenu $3"; FAIL=1; fi
}
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

check "Frontend Angular (/)"                    200 "$(code "$BASE/")"
check "Routage Angular (/employees)"            200 "$(code "$BASE/employees")"
check "API backend sans jeton refusee"          403 "$(code "$BASE/departement_employee/api")"

HEADERS=$(curl -s -D - -o /dev/null -X POST "$BASE/users/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}")
TOKEN=$(printf '%s' "$HEADERS" | tr -d '\r' | sed -n 's/^[Aa]uthorization: //p')
if [ -n "$TOKEN" ]; then echo "OK    Login admin via users-microservice (jeton JWT recu)"; else echo "ECHEC Login admin : aucun jeton recu"; FAIL=1; fi

check "Liste employes avec jeton (backend)"      200 "$(code -H "Authorization: $TOKEN" "$BASE/departement_employee/api")"
check "Liste departements avec jeton (backend)"  200 "$(code -H "Authorization: $TOKEN" "$BASE/departement_employee/dep")"
check "Liste utilisateurs ADMIN (microservice)"  200 "$(code -H "Authorization: $TOKEN" "$BASE/users/all")"

if [ "$FAIL" -eq 0 ]; then echo "SMOKE TESTS : TOUS REUSSIS"; else echo "SMOKE TESTS : ECHEC"; fi
exit "$FAIL"
