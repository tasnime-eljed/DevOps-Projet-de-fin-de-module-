package com.departementemployee.users.init;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.departementemployee.users.entities.Role;
import com.departementemployee.users.entities.User;
import com.departementemployee.users.repos.RoleRepository;
import com.departementemployee.users.repos.UserRepository;

/**
 * Initialise les donnees techniques indispensables au fonctionnement de l'application :
 *  - les roles ADMIN et USER (crees uniquement s'ils n'existent pas) ;
 *  - un compte administrateur initial (cree uniquement s'il n'existe pas).
 *
 * Regles de securite :
 *  - desactive par defaut (app.init.enabled=false) : rien n'est ecrit dans une base
 *    tant que ADMIN_INIT_ENABLED=true n'est pas fourni (c'est le cas dans Docker Compose) ;
 *  - aucun mot de passe en dur : identifiants lus dans ADMIN_USERNAME / ADMIN_PASSWORD / ADMIN_EMAIL ;
 *  - mot de passe hache avec le BCryptPasswordEncoder deja utilise par l'application ;
 *  - un utilisateur existant n'est jamais modifie ni supprime.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.init.enabled:false}")
    private boolean enabled;

    @Value("${app.admin.username:}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.email:}")
    private String adminEmail;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                           BCryptPasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Initialisation des roles/admin desactivee (ADMIN_INIT_ENABLED=false).");
            return;
        }

        Role adminRole = createRoleIfMissing("ADMIN");
        Role userRole = createRoleIfMissing("USER");

        if (isBlank(adminUsername) || isBlank(adminPassword)) {
            log.warn("ADMIN_USERNAME ou ADMIN_PASSWORD non defini : aucun compte administrateur cree.");
            return;
        }
        if (userRepository.findByUsername(adminUsername) != null) {
            log.info("Le compte administrateur '{}' existe deja : il n'est pas modifie.", adminUsername);
            return;
        }
        String email = isBlank(adminEmail) ? null : adminEmail.trim();
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            log.warn("L'email administrateur est deja utilise par un autre compte : aucun compte cree.");
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername.trim());
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setEnabled(true);
        List<Role> roles = new ArrayList<>();
        roles.add(adminRole);
        roles.add(userRole);
        admin.setRoles(roles);
        userRepository.save(admin);
        log.info("Compte administrateur '{}' cree avec les roles ADMIN et USER.", adminUsername);
    }

    private Role createRoleIfMissing(String name) {
        Role existing = roleRepository.findByRole(name);
        if (existing != null) {
            return existing;
        }
        log.info("Creation du role {}.", name);
        return roleRepository.save(new Role(null, name));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
