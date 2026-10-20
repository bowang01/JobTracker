package com.jobtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AccountStatusFilter;
import com.jobtrack.security.LoginSuccessHandler;
import com.jobtrack.service.UserService;
import com.jobtrack.web.Flash;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, UserService userService)
            throws Exception {
        http
            // Not a @Component, so Spring Boot does not also register it as a global servlet filter
            .addFilterBefore(new AccountStatusFilter(users), AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/js/**", "/icons/**", "/manifest.json", "/favicon.ico", "/error", "/error/**").permitAll()
                .requestMatchers("/login", "/register").permitAll()
                // Interview hall: public questions can be browsed without an account (read-only).
                // Its tab/search/page state is kept in the visitor's session like for signed-in users.
                .requestMatchers(HttpMethod.GET, "/hall").permitAll()
                .requestMatchers(HttpMethod.POST, "/hall/tab", "/hall/filter", "/hall/page", "/hall/reset").permitAll()
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                // Regular-user features: creating and changing applications, interviews and documents.
                // Admins only monitor (read-only); the services enforce the same rule (owner-only writes).
                .requestMatchers(HttpMethod.GET, "/applications/new", "/applications/*/edit",
                        "/applications/*/interviews/*/edit").hasRole("USER")
                // The interview question bank is a personal tool for regular users
                .requestMatchers("/questions", "/questions/**").hasRole("USER")
                // Interview hall: users copy questions, admins moderate
                .requestMatchers(HttpMethod.POST, "/hall/*/copy").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/hall/*/unpublish").hasRole("ADMIN")
                // List search/sort/paging state (POST, stored in the session) is for admins too
                .requestMatchers(HttpMethod.POST, "/applications/filter", "/applications/sort",
                        "/applications/page", "/applications/reset").authenticated()
                .requestMatchers(HttpMethod.POST, "/applications", "/applications/**", "/documents/**").hasRole("USER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .successHandler(new LoginSuccessHandler(userService))
                // Messages travel as flash attributes, so the address bar stays at /login (no ?error)
                .failureHandler((request, response, exception) -> {
                    Flash.put(request, response, "/login", "error",
                            "Incorrect email or password, or the account is disabled.");
                    response.sendRedirect(request.getContextPath() + "/login");
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    Flash.put(request, response, "/login", "success", "You have been signed out.");
                    response.sendRedirect(request.getContextPath() + "/login");
                })
                .permitAll())
            .headers(headers -> headers
                // Pages use no inline scripts or styles, so the CSP can be strict
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                        + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'")));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
