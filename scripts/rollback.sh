#!/usr/bin/env bash
# =====================================================================
#  Retour arriere : remet en service la version precedente
#
#    bash scripts/rollback.sh            # version precedente memorisee
#    bash scripts/rollback.sh v1.0.0     # ou une version precise
#
#  Les images de l'ancienne version sont deja publiees : rien n'est
#  reconstruit, on redemarre simplement les conteneurs avec l'ancien tag,
#  puis scripts/deploy.sh refait le controle de sante.
#  Les donnees MySQL (volume) sont conservees.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

TARGET="${1:-$(cat .deploy/previous-version 2>/dev/null || true)}"
if [ -z "$TARGET" ]; then
  echo "ECHEC : aucune version precedente connue. Usage : bash scripts/rollback.sh vX.Y.Z" >&2
  exit 2
fi

echo "==> RETOUR ARRIERE vers $TARGET (version actuelle : $(cat .deploy/current-version 2>/dev/null || echo inconnue))"
bash scripts/deploy.sh "$TARGET"
