package com.ryan.logisticexpress.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_SOURCE_BEARER = "BEARER";
    public static final String AUTH_SOURCE_COOKIE = "COOKIE";

    public record ResolvedToken(String token, String source) {}

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    @Getter
    private final String cookieName;

    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            @Value("${jwt.cookie-name:ACCESS_TOKEN}") String cookieName
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.cookieName = StringUtils.hasText(cookieName) ? cookieName : "ACCESS_TOKEN";
    }

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this(jwtService, userDetailsService, "ACCESS_TOKEN");
    }

    // Fallback no-arg constructor for lightweight test mocks
    public JwtAuthenticationFilter() {
        this(null, null, "ACCESS_TOKEN");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (hasQueryTokenParameter(request)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token in URL query parameter is forbidden");
            return;
        }

        ResolvedToken resolved = resolveTokenWithSource(request);

        if (resolved != null && StringUtils.hasText(resolved.token()) && jwtService != null && userDetailsService != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolved.token();
            try {
                String tokenType = jwtService.extractTokenType(token);
                if (tokenType == null || "ACCESS".equals(tokenType)) {
                    String username = jwtService.extractUsername(token);
                    UUID userId = jwtService.extractUserId(token);
                    List<String> roles = jwtService.extractRoles(token);
                    if (StringUtils.hasText(username)) {
                        UserDetails userDetails = userDetailsService.loadUserForJwt(userId, username, roles);
                        if (userDetails.isEnabled() && userDetails.isAccountNonLocked()
                                && jwtService.isTokenValid(token, userDetails)) {
                            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                            authToken.setDetails(resolved.source());
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                        }
                    }
                }
            } catch (JwtException | UsernameNotFoundException | IllegalArgumentException ex) {
                log.debug("JWT authentication skipped: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    public String resolveToken(HttpServletRequest request) {
        ResolvedToken resolved = resolveTokenWithSource(request);
        return resolved != null ? resolved.token() : null;
    }

    public ResolvedToken resolveTokenWithSource(HttpServletRequest request) {
        if (hasQueryTokenParameter(request)) {
            return null;
        }
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String value = bearer.substring(7).trim();
            return StringUtils.hasText(value) ? new ResolvedToken(value, AUTH_SOURCE_BEARER) : null;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return new ResolvedToken(cookie.getValue().trim(), AUTH_SOURCE_COOKIE);
                }
            }
        }
        return null;
    }

    private boolean hasQueryTokenParameter(HttpServletRequest request) {
        String query = request.getQueryString();
        if (StringUtils.hasText(query) && (query.startsWith("token=") || query.contains("&token="))) {
            return true;
        }
        String contentType = request.getContentType();
        boolean isJsonOrNoBody = contentType == null || contentType.contains("application/json");
        return isJsonOrNoBody && request.getParameter("token") != null;
    }
}
