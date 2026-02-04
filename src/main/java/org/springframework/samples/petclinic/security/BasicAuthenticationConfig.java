package org.springframework.samples.petclinic.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.env.Environment;

import java.util.HashMap;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity(prePostEnabled = true) // Enable @PreAuthorize method-level security
@ConditionalOnProperty(name = "petclinic.security.enable", havingValue = "true")
public class BasicAuthenticationConfig {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment env;

    @Autowired
    private AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
       
       
          // Conditionally enable OAuth2
        boolean oauthEnabled = Boolean.parseBoolean(
            env.getProperty("petclinic.security.oauth2.enable", "false")
        );

        if(oauthEnabled) {
             http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/auth/login", "/api/auth/status", "/api/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionFixation().migrateSession()
               /*  .invalidSessionStrategy((req, res) -> {

                 String path = req.getRequestURI();
                if (path.startsWith("/api/auth/login") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
                    // Allow login page or public endpoints to proceed even if session expired
                    return;
                }
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"SESSION_EXPIRED\",\"message\":\"Session expired. Please login again.\"}");
                })*/
                .maximumSessions(1)
                .expiredSessionStrategy(event -> {
                var res = event.getResponse();
                res.setStatus(401);
                res.getWriter().write("{\"error\":\"SESSION_EXPIRED\"}");
                })
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/api/auth/login")
                .successHandler(oauth2AuthenticationSuccessHandler)
                .failureUrl("/api/auth/login?error=true")
            )
           .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/api/auth/status")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");

                    HashMap<String, Object> body = new HashMap<>();
                    body.put("success", true);
                    body.put("message", "Successfully logged out");
                    body.put("authenticated", false);

                    new ObjectMapper().writeValue(response.getWriter(), body);
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

            return http.build();
        }
        else {

            // @formatter:off
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests((authz) -> authz
                    .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            // @formatter:on
            return http.build();
        }
    }

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        // @formatter:off
        auth
            .jdbcAuthentication()
                .dataSource(dataSource)
                .usersByUsernameQuery("select username,password,enabled from users where username=?")
                .authoritiesByUsernameQuery("select username,role from roles where username=?");
        // @formatter:on
    }
}
