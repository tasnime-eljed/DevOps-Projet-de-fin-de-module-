import { Component, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Employee } from '../model/employee.model';
import { EmployeeService } from '../services/employee';
import { Auth } from '../services/auth';

@Component({
  selector: 'app-employees',
  imports: [DatePipe, RouterLink],
  templateUrl: './employees.html',
  styleUrl: './employees.css'
})
export class Employees implements OnInit {

  employees: Employee[] = [];

  constructor(private employeeService: EmployeeService, public authService: Auth) {}

  ngOnInit(): void {
    this.chargerEmployees();
  }

  chargerEmployees(): void {
    this.employeeService.listeEmployees().subscribe(emps => {
      this.employees = emps;
    });
  }

  supprimerEmployee(e: Employee): void {
    const conf = confirm('Êtes-vous sûr ?');
    //si un utilisateur USER essaie de supprimer un employé, le backend refuse l'opération.
    // Le code ci-dessous gère l'erreur et affiche un message d'alerte à l'utilisateur.
    if (conf) {
      this.employeeService.supprimerEmployee(e.idEmployee!).subscribe({
        next: () => {
          this.chargerEmployees();
        },
        error: (err) => {
          if (err.status === 403) {
            alert("Vous n'avez pas le droit de supprimer un employé (rôle ADMIN requis).");
          } else {
            alert("Erreur lors de la suppression de l'employé.");
          }
        }
      });
    }
  }
}
