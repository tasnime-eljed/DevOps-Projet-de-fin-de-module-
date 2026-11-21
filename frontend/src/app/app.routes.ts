import { Routes } from '@angular/router';
import { Employees } from './employees/employees';
import { AddEmployee } from './add-employee/add-employee';
import { UpdateEmployee } from './update-employee/update-employee';
import { RechercheParDepartement } from './recherche-par-departement/recherche-par-departement';
import { RechercheParNom } from './recherche-par-nom/recherche-par-nom';
import { ListeDepartements } from './liste-departements/liste-departements';
import { Login } from './login/login';
import { Forbidden } from './forbidden/forbidden';
import { employeeGuard } from './employee-guard';
import { Register } from './register/register';
import { VerifEmail } from './verif-email/verif-email';

export const routes: Routes = [
  { path: 'employees', component: Employees },
  { path: 'add-employee', component: AddEmployee , canActivate: [employeeGuard]},
  { path: 'update-employee/:id', component: UpdateEmployee , canActivate: [employeeGuard]},
  { path: 'rechercheParDepartement', component: RechercheParDepartement },
  { path: 'rechercheParNom', component: RechercheParNom },
  { path: 'listeDepartements', component: ListeDepartements, canActivate: [employeeGuard] },
  { path: 'login', component: Login },
   { path: 'app-forbidden', component: Forbidden },
   { path: 'register', component: Register },
   { path: 'verifEmail', component: VerifEmail },
  { path: '', redirectTo: 'employees', pathMatch: 'full' }
];
