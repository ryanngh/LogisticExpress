package com.ryan.logisticexpress.config;

import com.ryan.logisticexpress.common.exception.BaseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
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

    public record ApiError(int status, String errorCode, String message, Instant timestamp) {}

    @ExceptionHandler(BaseException.class)
    public Object handleBaseException(BaseException ex, HttpServletRequest request) {
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
        return (uri != null && uri.startsWith("/api/")) || (accept != null && accept.contains("application/json"));
    }
}
