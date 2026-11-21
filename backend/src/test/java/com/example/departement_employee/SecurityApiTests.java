package com.example.departement_employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

/**
 * Verifie la securite JWT des endpoints REST du backend (base de test isolee, rollback).
 * Les jetons sont signes avec la meme cle que l'application (propriete jwt.secret),
 * comme le fait users-microservice au login.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SecurityApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private String token(String... roles) {
        return "Bearer " + JWT.create()
                .withSubject("utilisateur-test")
                .withArrayClaim("roles", roles)
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(jwtSecret));
    }

    @Test
    void listeEmployesSansJetonEstRefusee() throws Exception {
        mvc.perform(get("/api")).andExpect(status().is4xxClientError());
    }

    @Test
    void listeEmployesAvecJetonUserEstAutorisee() throws Exception {
        mvc.perform(get("/api").header("Authorization", token("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void listeDepartementsAvecJetonUserEstAutorisee() throws Exception {
        mvc.perform(get("/dep").header("Authorization", token("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void ajoutEmployeReserveAuRoleAdmin() throws Exception {
        String json = "{\"nomComplet\":\"Employe Securite Test\",\"salaire\":2500.0}";

        mvc.perform(post("/api").header("Authorization", token("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api").header("Authorization", token("ADMIN", "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void routeHealthPubliqueRenvoieUp() throws Exception {
        // sans jeton : la route de sante doit rester accessible (HEALTHCHECK Docker, CI)
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }
}
