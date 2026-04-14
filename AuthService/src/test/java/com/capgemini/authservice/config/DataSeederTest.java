package com.capgemini.authservice.config;

import com.capgemini.authservice.entity.RoleEntity;
import com.capgemini.authservice.entity.UserEntity;
import com.capgemini.authservice.repository.RoleRepository;
import com.capgemini.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dataSeeder, "adminPassword", "Admin@123");
    }

    @Test
    void run_shouldSeedRolesWhenNotPresent() throws Exception {
        when(roleRepository.findByName(any())).thenReturn(Optional.empty());
        when(roleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(new UserEntity()));

        dataSeeder.run();

        verify(roleRepository, times(4)).save(any(RoleEntity.class));
    }

    @Test
    void run_shouldNotSeedRolesWhenAlreadyPresent() throws Exception {
        RoleEntity existingRole = RoleEntity.builder().id(1L).name("ROLE_FOUNDER").build();
        when(roleRepository.findByName(any())).thenReturn(Optional.of(existingRole));
        when(userRepository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(new UserEntity()));

        dataSeeder.run();

        verify(roleRepository, never()).save(any());
    }

    @Test
    void run_shouldSeedAdminUserWhenNotPresent() throws Exception {
        RoleEntity adminRole = RoleEntity.builder().id(4L).name("ROLE_ADMIN").build();
        when(roleRepository.findByName(any())).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("admin@gmail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded-password");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dataSeeder.run();

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("admin@gmail.com") &&
                user.getName().equals("Admin")
        ));
    }

    @Test
    void run_shouldNotSeedAdminWhenAlreadyPresent() throws Exception {
        RoleEntity adminRole = RoleEntity.builder().id(4L).name("ROLE_ADMIN").build();
        when(roleRepository.findByName(any())).thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmail("admin@gmail.com")).thenReturn(Optional.of(new UserEntity()));

        dataSeeder.run();

        verify(userRepository, never()).save(any());
    }
}
