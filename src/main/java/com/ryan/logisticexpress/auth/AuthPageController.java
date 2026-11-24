package com.ryan.logisticexpress.auth;

import com.ryan.logisticexpress.auth.service.AuthService;
import com.ryan.logisticexpress.security.CustomUserDetails;
import com.ryan.logisticexpress.user.enums.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AuthPageController {

    private final AuthService authService;

    public AuthPageController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public String landingPage(Authentication authentication, Model model) {
        boolean authenticated = isAuthenticated(authentication);
        model.addAttribute("isAuthenticated", authenticated);
        if (authenticated) {
            model.addAttribute("currentUsername", authentication.getName());
            model.addAttribute("dashboardUrl", resolveDashboardUrl(authentication));
        }
        return "landing/index";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(name = "next", required = false) String next,
            @RequestParam(name = "error", required = false) String error,
            Authentication authentication,
            Model model
    ) {
        return renderAuthPage("signin", next, error, authentication, model);
    }

    @GetMapping("/register")
    public String registerPage(
            @RequestParam(name = "next", required = false) String next,
            Authentication authentication,
            Model model
    ) {
        return renderAuthPage("signup", next, null, authentication, model);
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(
            @RequestParam(name = "next", required = false) String next,
            Authentication authentication,
            Model model
    ) {
        return renderAuthPage("forgot", next, null, authentication, model);
    }

    private String renderAuthPage(
            String initialTab,
            String next,
            String error,
            Authentication authentication,
            Model model
    ) {
        boolean authenticated = isAuthenticated(authentication);
        model.addAttribute("initialTab", initialTab);
        model.addAttribute("nextUrl", sanitizeNextUrl(next));
        model.addAttribute("errorParam", error);
        model.addAttribute("isAuthenticated", authenticated);
        if (authenticated) {
            model.addAttribute("currentUsername", authentication.getName());
            model.addAttribute("dashboardUrl", resolveDashboardUrl(authentication));
        }
        return "auth/auth";
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails;
    }

    private String resolveDashboardUrl(Authentication authentication) {
        Set<Role> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> {
                    try {
                        return Role.valueOf(auth);
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(role -> role != null)
                .collect(Collectors.toSet());
        return authService.determineRedirectUrl(roles);
    }

    // Only allow relative same-origin paths for ?next= to prevent open redirects
    private String sanitizeNextUrl(String next) {
        if (!StringUtils.hasText(next)) {
            return "";
        }
        String trimmed = next.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//")) {
            return trimmed;
        }
        return "";
    }
}
