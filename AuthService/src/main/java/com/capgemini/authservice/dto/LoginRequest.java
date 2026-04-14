package com.capgemini.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank
    @Pattern(regexp = "(?i)^[a-z0-9.]+@(gmail\\.com|yahoo\\.com|outlook\\.com|hotmail\\.com|example\\.com)$", 
             message = "Please enter a valid Email")
    private String email;

    @NotBlank
    private String password;
    
    public void setEmail(String email) {
        this.email = email != null ? email.toLowerCase().trim() : null;
    }
}
