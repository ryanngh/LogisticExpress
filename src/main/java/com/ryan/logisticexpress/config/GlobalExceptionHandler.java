package com.ryan.logisticexpress.config;

import com.ryan.logisticexpress.auth.exceptions.InvalidRefreshTokenException;
import com.ryan.logisticexpress.common.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import java.time.Instant;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    public record ApiError(int status, String errorCode, String message, Instant timestamp) {}

    @ExceptionHandler(BaseException.class)
    public Object handleBaseException(BaseException ex, HttpServletRequest request) {
        if (ex instanceof InvalidRefreshTokenException && isApiRequest(request)) {
            ResponseCookie clearAccess = ResponseCookie.from("ACCESS_TOKEN", "")
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/")
                    .maxAge(0)
                    .sameSite("Lax")
                    .build();
            ResponseCookie clearRefresh = ResponseCookie.from("REFRESH_TOKEN", "")
                    .httpOnly(true)
                    .secure(cookieSecure)
                    .path("/auth")
                    .maxAge(0)
                    .sameSite("Strict")
                    .build();
            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.SET_COOKIE, clearAccess.toString(), clearRefresh.toString())
                    .body(new ApiError(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), Instant.now()));
        }
        return buildResponse(request, ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return buildResponse(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", msg);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(request, HttpStatus.FORBIDDEN, "FORBIDDEN", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public Object handleOptimisticLock(org.springframework.orm.ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return buildResponse(request, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", "Resource was modified concurrently. Please retry.");
    }

    @ExceptionHandler(Exception.class)
    public Object handleGeneral(Exception ex, HttpServletRequest request) {
        return buildResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", ex.getMessage());
    }

    private Object buildResponse(HttpServletRequest request, HttpStatus status, String errorCode, String message) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(status).body(new ApiError(status.value(), errorCode, message, Instant.now()));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(status);
        mav.addObject("status", status.value());
        mav.addObject("errorCode", errorCode);
        mav.addObject("message", message);
        return mav;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return (uri != null && (uri.startsWith("/api/") || uri.startsWith("/auth/")))
                || (accept != null && accept.contains("application/json"));
    }
}
