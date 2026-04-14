package com.capgemini.messaging.exception;

import com.capgemini.messaging.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Ensuring messaging errors are returned in a consistent, readable format
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        // Map common messages to appropriate status codes
        if (ex.getMessage().contains("Access denied")) {
            return new ResponseEntity<>(ApiResponse.error(403, ex.getMessage()), HttpStatus.FORBIDDEN);
        }
        if (ex.getMessage().contains("not found")) {
            return new ResponseEntity<>(ApiResponse.error(404, ex.getMessage()), HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(ApiResponse.error(500, ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return new ResponseEntity<>(ApiResponse.error(500, "An internal error occurred: " + ex.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
