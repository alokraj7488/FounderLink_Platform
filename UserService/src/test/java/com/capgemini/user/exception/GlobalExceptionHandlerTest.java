package com.capgemini.user.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleResourceNotFound_shouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Profile not found");
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Profile not found");
        assertThat(response.getBody()).containsEntry("status", 404);
    }

    @Test
    void handleDuplicateResource_shouldReturn409() {
        DuplicateResourceException ex = new DuplicateResourceException("Email already exists");
        ResponseEntity<Map<String, Object>> response = handler.handleDuplicateResource(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("message", "Email already exists");
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Access denied");
    }

    @Test
    void handleValidationErrors_shouldReturn400WithFieldErrors() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, result);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKey("errors");
    }

    @Test
    void handleGenericException_shouldReturn500() {
        Exception ex = new RuntimeException("Unexpected error");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
