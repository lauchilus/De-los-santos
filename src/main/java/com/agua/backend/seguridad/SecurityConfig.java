package com.agua.backend.seguridad;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UsuarioDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider provider
    ) throws Exception {

        http
                .authenticationProvider(provider)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/csrf",
                                "/api/v1/auth/me"
                        ).authenticated()

                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/clientes",
                                "/api/v1/clientes/**"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/clientes"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/productos"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/stock/fabrica"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/camionetas",
                                "/api/v1/camionetas/**"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jornadas"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jornadas/*"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jornadas/*/cargas"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jornadas/*/cargas/*",
                                "/api/v1/jornadas/*/stock"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jornadas/*/entregas"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jornadas/*/entregas/*"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jornadas/*/cobros"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/jornadas/*/cobros/*",
                                "/api/v1/jornadas/*/caja"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/categorias-gasto",
                                "/api/v1/jornadas/*/gastos",
                                "/api/v1/jornadas/*/gastos/*"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/jornadas/*/gastos"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")

                        .anyRequest().denyAll()
                )
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}