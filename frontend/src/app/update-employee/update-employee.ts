import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { EmployeeService } from '../services/employee';
import { Employee } from '../model/employee.model';
import { Departement } from '../model/departement.model';

@Component({
  selector: 'app-update-employee',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  templateUrl: './update-employee.html',
  styleUrl: './update-employee.css'
})
export class UpdateEmployee implements OnInit {

  currentEmployee: Employee = {};
  departements: Departement[] = [];
  updatedDepId: number | null = null;

  constructor(
    private activatedRoute: ActivatedRoute,
    private employeeService: EmployeeService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.employeeService.listeDepartements().subscribe(deps => {
      this.departements = deps._embedded.departements;
    });

    // remplir le formulaire avec les données de l'employé à modifier
    this.employeeService
      .consulterEmployee(this.activatedRoute.snapshot.params['id'])
      .subscribe(emp => {
        this.currentEmployee = emp;
        this.updatedDepId = emp.departement?.idDepartement ?? null;
      });
  }

  updateEmployee(): void {
    // mettre à jour le département de l'employé avec le département sélectionné
    this.currentEmployee.departement = this.departements.find(
      dep => dep.idDepartement == this.updatedDepId
    );
   //si un utilisateur USER essaie de modifier un employé, le backend refuse l'opération.
   // Le code ci-dessous gère l'erreur et affiche un message d'alerte à l'utilisateur.
    this.employeeService.updateEmployee(this.currentEmployee).subscribe({
      next: () => {
        // redirection vers la liste des employés après la mise à jour
        this.router.navigate(['/employees']);
      },
      error: (err) => {
        if (err.status === 403) {
          alert("Vous n'avez pas le droit de modifier un employé (rôle ADMIN requis).");
        } else {
          alert("Erreur lors de la modification de l'employé.");
        }
      }
    });
  }

  onDateChange(value: string): void {
    this.currentEmployee.dateEmbauche = value
      ? new Date(value + 'T00:00:00')
      : undefined;
  }
}
