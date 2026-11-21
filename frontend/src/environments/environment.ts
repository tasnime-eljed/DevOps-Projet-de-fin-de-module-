// Configuration de PRODUCTION (build Docker) : chemins relatifs.
// Nginx (reverse proxy) redirige /departement_employee/* vers le backend
// et /users/* vers users-microservice.
export const environment = {
  apiURL: '/departement_employee/api',
  apiURLDep: '/departement_employee/dep',
  usersURL: '/users'
};
