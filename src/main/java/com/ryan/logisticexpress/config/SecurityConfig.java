package com.ryan.logisticexpress.config;

import com.ryan.logisticexpress.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/track/**",
                                "/api/public/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/shop/**", "/api/shop/**").hasRole("SHOP")
                        .requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")
                        .requestMatchers("/ops/**", "/api/ops/**").hasAnyRole(
                                "ADMIN",
                                "DISPATCHER",
                                "HUB_STAFF",
                                "ACCOUNTANT",
                                "MANAGER",
                                "CSKH"
                        )
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authEx) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessEx) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                            } else {
                                response.sendRedirect("/login?error=forbidden");
                            }
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
