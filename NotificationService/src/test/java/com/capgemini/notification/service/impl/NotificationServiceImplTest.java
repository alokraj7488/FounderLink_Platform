package com.capgemini.notification.service.impl;

import com.capgemini.notification.dto.NotificationResponse;
import com.capgemini.notification.entity.Notification;
import com.capgemini.notification.enums.NotificationType;
import com.capgemini.notification.mapper.NotificationMapper;
import com.capgemini.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationMapper notificationMapper;

    @InjectMocks
    private NotificationServiceImpl service;

    private Notification notification;
    private NotificationResponse responseDto;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .id(1L).userId(10L)
                .message("Welcome!").type(NotificationType.USER_REGISTERED)
                .isRead(false).build();

        responseDto = NotificationResponse.builder()
                .id(1L).userId(10L)
                .message("Welcome!").type(NotificationType.USER_REGISTERED)
                .isRead(false).build();
    }

    @Test
    void createNotification_shouldSaveNotification() {
        service.createNotification(10L, "Welcome!", NotificationType.USER_REGISTERED);

        verify(notificationRepository).save(argThat(n ->
                n.getUserId().equals(10L) &&
                n.getMessage().equals("Welcome!") &&
                n.getType() == NotificationType.USER_REGISTERED &&
                !n.getIsRead()
        ));
    }

    @Test
    void getNotificationsByUser_shouldReturnMappedList() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(responseDto);

        List<NotificationResponse> result = service.getNotificationsByUser(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessage()).isEqualTo("Welcome!");
    }

    @Test
    void getUnreadNotifications_shouldReturnOnlyUnread() {
        when(notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(notification));
        when(notificationMapper.toResponse(notification)).thenReturn(responseDto);

        List<NotificationResponse> result = service.getUnreadNotifications(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    void markAsRead_whenNotificationExists_shouldSetReadTrue() {
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(notification);
        NotificationResponse readDto = NotificationResponse.builder()
                .id(1L).isRead(true).build();
        when(notificationMapper.toResponse(any())).thenReturn(readDto);

        NotificationResponse result = service.markAsRead(1L);

        assertThat(result.getIsRead()).isTrue();
    }

    @Test
    void markAsRead_whenNotFound_shouldThrowEntityNotFoundException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
