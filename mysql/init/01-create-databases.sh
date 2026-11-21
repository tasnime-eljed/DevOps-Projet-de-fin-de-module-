#!/bin/bash
# =====================================================================
#  Initialisation MySQL (executee UNE SEULE FOIS, quand le volume est vide)
#  - departement_employee est deja creee par MYSQL_DATABASE
#  - cree users_db
#  - donne au compte applicatif ($MYSQL_USER) les droits sur les deux bases
#  Aucune donnee metier n'est inseree : les tables sont creees par Hibernate.
# =====================================================================
set -eo pipefail

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS departement_employee CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE DATABASE IF NOT EXISTS users_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    GRANT ALL PRIVILEGES ON departement_employee.* TO '${MYSQL_USER}'@'%';
    GRANT ALL PRIVILEGES ON users_db.* TO '${MYSQL_USER}'@'%';
    FLUSH PRIVILEGES;
EOSQL

echo "Bases departement_employee et users_db pretes pour l'utilisateur ${MYSQL_USER}."
