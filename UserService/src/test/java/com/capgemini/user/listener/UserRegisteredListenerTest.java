package com.capgemini.user.listener;

import com.capgemini.user.entity.UserProfile;
import com.capgemini.user.event.UserRegisteredEvent;
import com.capgemini.user.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRegisteredListenerTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserRegisteredListener listener;

    @Test
    void handleUserRegistered_whenProfileNotExists_shouldSaveNewProfile() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(10L);
        event.setName("Alice");
        event.setEmail("alice@example.com");
        event.setRole("ROLE_INVESTOR");

        when(userProfileRepository.existsByUserId(10L)).thenReturn(false);

        // when
        listener.handleUserRegistered(event);

        // then
        verify(userProfileRepository).save(any(UserProfile.class));
    }

    @Test
    void handleUserRegistered_whenProfileAlreadyExists_shouldSkipSaving() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent();
        event.setUserId(10L);

        when(userProfileRepository.existsByUserId(10L)).thenReturn(true);

        // when
        listener.handleUserRegistered(event);

        // then
        verify(userProfileRepository, never()).save(any(UserProfile.class));
    }
}
