package com.example.departement_employee.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import java.util.Arrays;
import java.util.Collections;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    // clé secrète JWT (propriété jwt.secret alimentée par JWT_SECRET)
    @Value("${jwt.secret}")
    private String jwtSecret;

    // origines autorisées pour CORS (propriété app.cors.allowed-origins, séparées par des virgules)
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(new CorsConfigurationSource() {
                    @Override
                    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                        CorsConfiguration cors = new CorsConfiguration();
                        cors.setAllowedOrigins(Arrays.stream(allowedOrigins).map(String::trim).toList());//autorise uniquement l'application
                        cors.setAllowedMethods(Collections.singletonList("*"));//On autorise toutes les méthodes HTTP :
                        cors.setAllowedHeaders(Collections.singletonList("*"));//On autorise tous les en-têtes HTTP envoyés par Angular.
                        cors.setExposedHeaders(Collections.singletonList("Authorization"));//Elle permet au code Angular de lire le
                        // header Authorization dans la réponse du backend Elle permet au code Angular de lire
                        // le header Authorization dans la réponse du backend
                        return cors;//On retourne les règles CORS à Spring Security.
                    }
                }))
                .authorizeHttpRequests(requests -> requests
                        // route de santé publique (HEALTHCHECK Docker, CI, déploiement)
                        .requestMatchers(HttpMethod.GET, "/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/**").hasAnyAuthority("ADMIN", "USER")
                        //.requestMatchers(HttpMethod.POST, "/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/**").hasAuthority("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JWTAuthorizationFilter(jwtSecret),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
