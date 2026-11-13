package com.ryan.logisticexpress.security;

import com.ryan.logisticexpress.user.entity.User;
import com.ryan.logisticexpress.user.entity.UserCredential;
import com.ryan.logisticexpress.user.enums.Role;
import com.ryan.logisticexpress.user.enums.Status;
import com.ryan.logisticexpress.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtAndSecurityTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtService jwtService;
    private UserRepository userRepository;
    private CustomUserDetailsService userDetailsService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = new JwtService(SECRET, 3600000L);
        userRepository = Mockito.mock(UserRepository.class);
        userDetailsService = new CustomUserDetailsService(userRepository);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User buildMockUser(String username, String email, Status status, int tokenVersion) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username(username)
                .email(email)
                .status(status)
                .tokenVersion(tokenVersion)
                .roles(Set.of(Role.ROLE_ADMIN, Role.ROLE_SHOP))
                .build();
        UserCredential credential = UserCredential.builder()
                .userId(user.getId())
                .user(user)
                .passwordHash("$2a$10$hashedpassword")
                .mustChangePassword(false)
                .failedLoginCount(0)
                .build();
        user.setCredential(credential);
        return user;
    }

    @Test
    void customUserDetails_mapsUserProperly() {
        User user = buildMockUser("admin", "admin@express.vn", Status.ACTIVE, 1);
        CustomUserDetails details = new CustomUserDetails(user);

        assertEquals("admin", details.getUsername());
        assertEquals("admin@express.vn", details.getEmail());
        assertEquals(user.getId(), details.getId());
        assertEquals(1, details.getTokenVersion());
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
        assertEquals(2, details.getAuthorities().size());
    }

    @Test
    void customUserDetails_lockedUser_isAccountNonLockedFalse() {
        User user = buildMockUser("locked_user", "locked@express.vn", Status.ACTIVE, 0);
        user.getCredential().setLockedUntil(Instant.now().plusSeconds(3600));
        CustomUserDetails details = new CustomUserDetails(user);

        assertFalse(details.isAccountNonLocked());
    }

    @Test
    void customUserDetailsService_loadsByUsernameOrEmail() {
        User user = buildMockUser("shop01", "shop01@express.vn", Status.ACTIVE, 0);
        when(userRepository.findByUsername("shop01")).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("shop01@express.vn")).thenReturn(Optional.of(user));

        CustomUserDetails fromUsername = userDetailsService.loadUserByUsername("shop01");
        assertNotNull(fromUsername);
        assertEquals("shop01", fromUsername.getUsername());

        CustomUserDetails fromEmail = userDetailsService.loadUserByUsername("shop01@express.vn");
        assertNotNull(fromEmail);
        assertEquals("shop01", fromEmail.getUsername());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername("unknown"));
    }

    @Test
    void jwtService_generatesAndValidatesToken() {
        User user = buildMockUser("shipper_dan", "shipper@express.vn", Status.ACTIVE, 2);
        CustomUserDetails details = new CustomUserDetails(user);

        String token = jwtService.generateToken(details);
        assertNotNull(token);

        assertEquals("shipper_dan", jwtService.extractUsername(token));
        assertEquals(user.getId(), jwtService.extractUserId(token));
        assertEquals(2, jwtService.extractTokenVersion(token));
        assertFalse(jwtService.isTokenExpired(token));
        assertTrue(jwtService.isTokenValid(token, details));

        // Invalidate on mismatched token_version
        User updatedUser = buildMockUser("shipper_dan", "shipper@express.vn", Status.ACTIVE, 3);
        CustomUserDetails updatedDetails = new CustomUserDetails(updatedUser);
        assertFalse(jwtService.isTokenValid(token, updatedDetails));
    }

    @Test
    void jwtAuthenticationFilter_validBearer_setsSecurityContext() throws ServletException, IOException {
        User user = buildMockUser("hub_manager", "hub@express.vn", Status.ACTIVE, 0);
        CustomUserDetails details = new CustomUserDetails(user);
        when(userRepository.findByUsername("hub_manager")).thenReturn(Optional.of(user));

        String token = jwtService.generateToken(details);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("hub_manager", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void jwtAuthenticationFilter_cookieToken_setsSecurityContext() throws ServletException, IOException {
        User user = buildMockUser("cookie_user", "cookie@express.vn", Status.ACTIVE, 0);
        CustomUserDetails details = new CustomUserDetails(user);
        when(userRepository.findByUsername("cookie_user")).thenReturn(Optional.of(user));

        String token = jwtService.generateToken(details);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("ACCESS_TOKEN", token));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("cookie_user", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void jwtAuthenticationFilter_invalidToken_doesNotSetSecurityContext() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.malformed.token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
