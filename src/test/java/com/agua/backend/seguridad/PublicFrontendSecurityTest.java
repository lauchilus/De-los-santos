package com.agua.backend.seguridad;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicFrontendSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @Configuration @EnableWebSecurity @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean UsuarioDetailsService users() { return mock(UsuarioDetailsService.class); }
        @Bean Routes routes() { return new Routes(); }
    }
    @RestController static class Routes {
        @GetMapping({"/", "/index.html", "/sw.js", "/assets/app.js", "/manifest.webmanifest",
                "/icon-192.png", "/icon-512.png", "/actuator/health/liveness",
                "/api/v1/admin/probe", "/api/v1/productos"})
        String ok() { return "ok"; }
        @PostMapping("/api/v1/clientes") String write() { return "ok"; }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void shellAndLivenessArePublic() throws Exception {
        for (String path : new String[]{"/", "/index.html", "/sw.js", "/assets/app.js",
                "/manifest.webmanifest", "/icon-192.png", "/icon-512.png", "/actuator/health/liveness"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
    }
    @Test void apiStillRequiresAuthenticationWithoutBrowserDialog() throws Exception {
        mvc.perform(get("/api/v1/productos")).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void adminStillRequiresAdminRole() throws Exception {
        mvc.perform(get("/api/v1/admin/probe").with(user("rep").roles("REPARTIDOR")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/probe").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
    @Test void writesStillRequireCsrf() throws Exception {
        mvc.perform(post("/api/v1/clientes").with(user("rep").roles("REPARTIDOR")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/clientes").with(user("rep").roles("REPARTIDOR")).with(csrf()))
                .andExpect(status().isOk());
    }
    @Test void unknownPathsAreNotExposed() throws Exception {
        mvc.perform(get("/application.yaml").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}
