package com.capgemini.authservice.exception;

import com.capgemini.authservice.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleCustomException_shouldReturnCorrectStatusAndMessage() {
        CustomException ex = new CustomException("User not found", HttpStatus.NOT_FOUND);

        ResponseEntity<ApiResponse<Void>> response = handler.handleCustomException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    void handleCustomException_withConflictStatus_shouldReturn409() {
        CustomException ex = new CustomException("Email already registered", HttpStatus.CONFLICT);

        ResponseEntity<ApiResponse<Void>> response = handler.handleCustomException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getStatus()).isEqualTo(409);
    }

    @Test
    void handleValidationException_shouldReturn400WithFieldErrors() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<ApiResponse<Void>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).contains("email").contains("name");
    }

    @Test
    void handleGenericException_shouldReturn500() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).contains("unexpected error");
    }
}
