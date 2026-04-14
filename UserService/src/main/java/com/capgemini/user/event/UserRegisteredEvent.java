package com.capgemini.user.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Standardized user registration event for distributed profile creation
public class UserRegisteredEvent {
    private Long userId;
    private String name;
    private String email;
    private String role;
}
