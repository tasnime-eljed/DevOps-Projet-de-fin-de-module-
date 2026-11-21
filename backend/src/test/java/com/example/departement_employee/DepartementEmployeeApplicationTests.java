package com.example.departement_employee;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.departement_employee.entities.Departement;
import com.example.departement_employee.entities.Employee;
import com.example.departement_employee.repos.DepartementRepository;
import com.example.departement_employee.repos.EmployeeRepository;

/**
 * Tests du repository des employes.
 *
 * Securite des donnees :
 *  - profil "test" : base ISOLEE departement_employee_test (voir src/test/resources/application-test.properties),
 *    jamais la base departement_employee reelle ;
 *  - chaque test cree ses propres donnees dans @BeforeEach ;
 *  - @Transactional : tout est annule (rollback) a la fin de chaque test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepartementEmployeeApplicationTests {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartementRepository departementRepository;

    private Departement depInfo;
    private Departement depRh;
    private Employee alice;
    private Employee bruno;

    @BeforeEach
    void creerDonneesDeTest() {
        depInfo = departementRepository.save(departement("Informatique-Test", "Departement de test"));
        depRh = departementRepository.save(departement("RH-Test", "Departement de test"));
        alice = employeeRepository.save(employee("Alice Testeur", 4200.0, depInfo));
        bruno = employeeRepository.save(employee("Bruno Testeur", 3100.0, depRh));
    }

    @Test
    void testCreateEmployee() {
        Employee saved = employeeRepository.save(employee("Chloe Testeur", 2800.0, depInfo));
        assertTrue(saved.getIdEmployee() != null);
        assertTrue(employeeRepository.findById(saved.getIdEmployee()).isPresent());
    }

    @Test
    void testFindEmployee() {
        Employee e = employeeRepository.findById(alice.getIdEmployee()).orElseThrow();
        assertEquals("Alice Testeur", e.getNomComplet());
        assertEquals(depInfo.getIdDepartement(), e.getDepartement().getIdDepartement());
    }

    @Test
    void testUpdateEmployee() {
        Employee e = employeeRepository.findById(alice.getIdEmployee()).orElseThrow();
        e.setSalaire(3000.0);
        employeeRepository.save(e);
        assertEquals(3000.0, employeeRepository.findById(alice.getIdEmployee()).orElseThrow().getSalaire());
    }

    @Test
    void testListerTousEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        assertTrue(employees.stream().anyMatch(e -> e.getIdEmployee().equals(alice.getIdEmployee())));
        assertTrue(employees.stream().anyMatch(e -> e.getIdEmployee().equals(bruno.getIdEmployee())));
    }

    @Test
    void testDeleteEmployee() {
        employeeRepository.deleteById(bruno.getIdEmployee());
        assertFalse(employeeRepository.findById(bruno.getIdEmployee()).isPresent());
    }

    @Test
    void testFindByNomComplet() {
        List<Employee> employees = employeeRepository.findByNomComplet("Alice Testeur");
        assertEquals(1, employees.size());
        assertEquals(alice.getIdEmployee(), employees.get(0).getIdEmployee());
    }

    @Test
    void testFindByNomCompletContains() {
        List<Employee> employees = employeeRepository.findByNomCompletContains("Testeur");
        assertEquals(2, employees.size());
    }

    @Test
    void testFindByNomSalaire() {
        // nom contient "Testeur" ET salaire > 3500 -> seulement Alice (4200)
        List<Employee> employees = employeeRepository.findByNomSalaire("Testeur", 3500.0);
        assertEquals(1, employees.size());
        assertEquals("Alice Testeur", employees.get(0).getNomComplet());
    }

    @Test
    void testFindByNomSalaire2() {
        List<Employee> employees = employeeRepository.findByNomSalaire2("Testeur", 3000.0);
        assertEquals(2, employees.size());
    }

    @Test
    void testFindByDepartement() {
        List<Employee> employees = employeeRepository.findByDepartement(depRh);
        assertEquals(1, employees.size());
        assertEquals("Bruno Testeur", employees.get(0).getNomComplet());
    }

    @Test
    void testFindByDepartementIdDepartement() {
        List<Employee> employees = employeeRepository.findByDepartementIdDepartement(depInfo.getIdDepartement());
        assertEquals(1, employees.size());
        assertEquals("Alice Testeur", employees.get(0).getNomComplet());
    }

    @Test
    void testFindByOrderByNomCompletAsc() {
        List<Employee> employees = employeeRepository.findByOrderByNomCompletAsc();
        assertEquals(List.of("Alice Testeur", "Bruno Testeur"),
                employees.stream().map(Employee::getNomComplet).toList());
    }

    @Test
    void testTrierEmployeesNomsSalaires() {
        List<Employee> employees = employeeRepository.trierEmployeesNomsSalaires();
        assertEquals("Alice Testeur", employees.get(0).getNomComplet());
    }

    private static Departement departement(String nom, String description) {
        Departement d = new Departement();
        d.setNomDepartement(nom);
        d.setDescriptionDepartement(description);
        return d;
    }

    private static Employee employee(String nom, Double salaire, Departement dep) {
        Employee e = new Employee();
        e.setNomComplet(nom);
        e.setSalaire(salaire);
        e.setDateEmbauche(new Date());
        e.setDepartement(dep);
        return e;
    }
}
