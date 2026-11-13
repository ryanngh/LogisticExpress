package com.ryan.logisticexpress.config;

import com.ryan.logisticexpress.common.exception.BadRequestException;
import com.ryan.logisticexpress.common.exception.ResourceNotFoundException;
import com.ryan.logisticexpress.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.ModelAndView;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndExceptionConfigTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final SecurityConfig securityConfig = new SecurityConfig(new JwtAuthenticationFilter());

    @Test
    void passwordEncoder_matchesBCrypt() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String raw = "secret123";
        String encoded = encoder.encode(raw);
        assertTrue(encoder.matches(raw, encoded));
    }

    @Test
    void handleBaseException_forApiRequest_returnsJson() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/shipments/123");
        ResourceNotFoundException ex = new ResourceNotFoundException("SHIPMENT_NOT_FOUND", "Shipment not found");

        Object result = handler.handleBaseException(ex, request);

        assertInstanceOf(ResponseEntity.class, result);
        @SuppressWarnings("unchecked")
        ResponseEntity<GlobalExceptionHandler.ApiError> response = (ResponseEntity<GlobalExceptionHandler.ApiError>) result;
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SHIPMENT_NOT_FOUND", response.getBody().errorCode());
        assertEquals("Shipment not found", response.getBody().message());
    }

    @Test
    void handleBaseException_forThymeleafRequest_returnsErrorView() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop/orders");
        BadRequestException ex = new BadRequestException("INVALID_INPUT", "Invalid input data");

        Object result = handler.handleBaseException(ex, request);

        assertInstanceOf(ModelAndView.class, result);
        ModelAndView mav = (ModelAndView) result;
        assertEquals("error", mav.getViewName());
        assertEquals(400, mav.getModel().get("status"));
        assertEquals("INVALID_INPUT", mav.getModel().get("errorCode"));
        assertEquals("Invalid input data", mav.getModel().get("message"));
    }
}
