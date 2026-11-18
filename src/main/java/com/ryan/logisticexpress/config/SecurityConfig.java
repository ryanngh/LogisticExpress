package com.ryan.logisticexpress.config;

import com.ryan.logisticexpress.security.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final boolean cookieSecure;

    @Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.cookie.secure:false}") boolean cookieSecure
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.cookieSecure = cookieSecure;
    }

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this(jwtAuthenticationFilter, false);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(cookieSecure).sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = csrfTokenRepository();
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(SecurityConfig::hasBearerAuthorizationHeader)
                )
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/login", "/register").permitAll()
                        .requestMatchers(
                                "/",
                                "/track/**",
                                "/api/public/**",
                                "/auth/login",
                                "/auth/register",
                                "/auth/refresh",
                                "/auth/logout",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico",
                                "/webjars/**"
                        ).permitAll()
                        .requestMatchers("/auth/change-password").authenticated()
                        .requestMatchers("/shop/**", "/api/shop/**").hasRole("SHOP")
                        .requestMatchers("/shipper/**", "/api/shipper/**").hasRole("SHIPPER")
                        .requestMatchers("/ops/users/**", "/api/ops/users/**").hasAnyRole("ADMIN", "DISPATCHER")
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
                            if (isApiOrAjaxRequest(request)) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                            } else {
                                String target = request.getRequestURI();
                                if (StringUtils.hasText(request.getQueryString())) {
                                    target += "?" + request.getQueryString();
                                }
                                String encodedNext = URLEncoder.encode(target, StandardCharsets.UTF_8);
                                response.sendRedirect("/login?next=" + encodedNext);
                            }
                        })
                        .accessDeniedHandler((request, response, accessEx) -> {
                            if (isApiOrAjaxRequest(request)) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
                            } else {
                                response.sendRedirect("/login?error=forbidden");
                            }
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, CsrfFilter.class)
                .addFilterBefore(new OriginValidationFilter(), CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .build();
    }

    public static boolean hasBearerAuthorizationHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return StringUtils.hasText(header) && header.startsWith("Bearer ");
    }

    private static boolean isApiOrAjaxRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        String requestedWith = request.getHeader("X-Requested-With");
        return (uri != null && (uri.startsWith("/api/") || uri.startsWith("/auth/")))
                || (accept != null && accept.contains("application/json"))
                || "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }

    public static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
        }
    }

    public static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

    public static final class OriginValidationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            String method = request.getMethod();
            if (method != null && !SAFE_METHODS.contains(method.toUpperCase()) && !hasBearerAuthorizationHeader(request)) {
                String secFetchSite = request.getHeader("Sec-Fetch-Site");
                if ("cross-site".equalsIgnoreCase(secFetchSite)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cross-site request forbidden");
                    return;
                }
                String origin = request.getHeader(HttpHeaders.ORIGIN);
                if (StringUtils.hasText(origin) && !isSameOrigin(request, origin)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid Origin header");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        }

        private boolean isSameOrigin(HttpServletRequest request, String origin) {
            try {
                URI uri = URI.create(origin.trim());
                if (uri.getScheme() == null || uri.getHost() == null) {
                    return false;
                }
                if (!uri.getScheme().equalsIgnoreCase(request.getScheme())
                        || !uri.getHost().equalsIgnoreCase(request.getServerName())) {
                    return false;
                }
                int originPort = uri.getPort() == -1
                        ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80)
                        : uri.getPort();
                int requestPort = request.getServerPort() <= 0
                        ? ("https".equalsIgnoreCase(request.getScheme()) ? 443 : 80)
                        : request.getServerPort();
                return originPort == requestPort;
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }
    }
}
