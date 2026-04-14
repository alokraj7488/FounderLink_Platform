package com.capgemini.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Received when a new user registers in the AuthService
public class UserRegisteredEvent {
    private Long userId;
    private String name;
    private String email;
    private String role;
}
