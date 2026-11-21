package com.departementemployee.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.departementemployee.users.entities.User;
import com.departementemployee.users.init.DataInitializer;
import com.departementemployee.users.repos.RoleRepository;
import com.departementemployee.users.repos.UserRepository;

/**
 * Tests de users-microservice sur une base ISOLEE (users_db_test, profil "test").
 * Le profil de test active DataInitializer avec un compte admin de test.
 */
@SpringBootTest
@ActiveProfiles("test")
class UsersMicroserviceApplicationTests {

    private static final String ADMIN = "admin-test";
    private static final String ADMIN_PASSWORD = "admin-test-password";

    @Autowired private WebApplicationContext context;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private DataInitializer dataInitializer;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void rolesEtAdminSontCreesAvecMotDePasseHache() {
        assertNotNull(roleRepository.findByRole("ADMIN"));
        assertNotNull(roleRepository.findByRole("USER"));

        User admin = userRepository.findByUsername(ADMIN);
        assertNotNull(admin);
        assertTrue(admin.getEnabled());
        assertTrue(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPassword()));
        assertTrue(admin.getRoles().stream().anyMatch(r -> "ADMIN".equals(r.getRole())));
    }

    @Test
    void initialisationEstIdempotente() throws Exception {
        long roles = roleRepository.count();
        long users = userRepository.count();
        String hash = userRepository.findByUsername(ADMIN).getPassword();

        dataInitializer.run();

        assertEquals(roles, roleRepository.count());
        assertEquals(users, userRepository.count());
        assertEquals(hash, userRepository.findByUsername(ADMIN).getPassword());
    }

    @Test
    void loginRenvoieUnJetonJwt() throws Exception {
        MvcResult result = mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String header = result.getResponse().getHeader("Authorization");
        assertNotNull(header);
        assertTrue(header.startsWith("Bearer "));

        // le jeton donne acces a /all (reserve ADMIN)
        mvc.perform(get("/all").header("Authorization", header)).andExpect(status().isOk());
    }

    @Test
    void loginAvecMauvaisMotDePasseEstRefuse() throws Exception {
        mvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN + "\",\"password\":\"mauvais\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listeUtilisateursSansJetonEstRefusee() throws Exception {
        mvc.perform(get("/all")).andExpect(status().is4xxClientError());
    }

    @Test
    void routeHealthPubliqueRenvoieUp() throws Exception {
        // sans jeton : la route de sante doit rester accessible (HEALTHCHECK Docker, CI)
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("UP"));
    }
}
