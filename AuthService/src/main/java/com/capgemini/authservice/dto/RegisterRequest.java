package com.capgemini.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "(?i)^[a-z0-9.]+@(gmail\\.com|yahoo\\.com|outlook\\.com|hotmail\\.com|example\\.com)$", 
             message = "Please enter a valid Email")
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    @NotBlank
    private String role;
    
    public void setEmail(String email) {
        this.email = email != null ? email.toLowerCase().trim() : null;
    }
}
