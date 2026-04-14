package com.capgemini.team.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

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
        ResourceNotFoundException ex = new ResourceNotFoundException("Invitation not found");
        ResponseEntity<Map<String, Object>> response = handler.handleResourceNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Invitation not found");
    }

    @Test
    void handleUnauthorized_shouldReturn403() {
        UnauthorizedException ex = new UnauthorizedException("Not the founder");
        ResponseEntity<Map<String, Object>> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handleBadRequest_shouldReturn400() {
        BadRequestException ex = new BadRequestException("Not in PENDING status");
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleDuplicate_shouldReturn409() {
        DuplicateResourceException ex = new DuplicateResourceException("Already a team member");
        ResponseEntity<Map<String, Object>> response = handler.handleDuplicate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void handleServiceUnavailable_shouldReturn503() {
        ServiceUnavailableException ex = new ServiceUnavailableException("Service down");
        ResponseEntity<Map<String, Object>> response = handler.handleServiceUnavailable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden");
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("message", "Access Denied: You don't have permission for this action");
    }

    @Test
    void handleGeneral_shouldReturn500() {
        Exception ex = new RuntimeException("Unexpected");
        ResponseEntity<Map<String, Object>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
