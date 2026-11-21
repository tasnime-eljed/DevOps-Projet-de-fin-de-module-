package com.example.departement_employee.restcontrollers;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route de santé publique (sans jeton) : GET /departement_employee/health
 * Utilisée par le HEALTHCHECK Docker, Docker Compose, la CI et le script de déploiement.
 * Renvoie 200 si l'application ET la base MySQL répondent, 503 sinon.
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    // Version déployée (tag de l'image, fournie par Docker Compose : APP_VERSION)
    @Value("${APP_VERSION:dev}")
    private String version;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "backend");
        body.put("version", version);

        boolean dbUp;
        try (var connection = dataSource.getConnection()) {
            dbUp = connection.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }
        body.put("database", dbUp ? "UP" : "DOWN");
        body.put("status", dbUp ? "UP" : "DOWN");
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
