package com.capgemini.authservice.controller;

import com.capgemini.authservice.mapper.AuthMapper;
import com.capgemini.authservice.service.IAuthService;
import com.capgemini.authservice.dto.ApiResponse;
import com.capgemini.authservice.dto.AuthResponse;
import com.capgemini.authservice.dto.LoginRequest;
import com.capgemini.authservice.dto.RegisterRequest;
import com.capgemini.authservice.dto.RegisterResponse;
import com.capgemini.authservice.dto.UserSummaryDto;
import com.capgemini.authservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final UserRepository userRepository;
    private final AuthMapper authMapper;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    // Returns a single user's summary (name, email, role) by their ID
    @GetMapping("/users/{id}")
    public ResponseEntity<UserSummaryDto> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(u -> ResponseEntity.ok(authMapper.toUserSummaryDto(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    // Returns all users with the specified role — used by founders to find co-founders to invite
    @GetMapping("/users/by-role")
    public ResponseEntity<List<UserSummaryDto>> getUsersByRole(@RequestParam String role) {
        List<UserSummaryDto> users = userRepository.findByRolesName(role).stream()
                .map(authMapper::toUserSummaryDto)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        AuthResponse response = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }
}
