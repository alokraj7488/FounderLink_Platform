package com.capgemini.notification.mapper;

import com.capgemini.notification.dto.NotificationResponse;
import com.capgemini.notification.entity.Notification;
import com.capgemini.notification.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {

    private NotificationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotificationMapper();
    }

    @Test
    void toResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Notification notification = Notification.builder()
                .id(1L).userId(10L)
                .message("Welcome to FounderLink!")
                .type(NotificationType.USER_REGISTERED)
                .isRead(false).createdAt(now)
                .build();

        NotificationResponse response = mapper.toResponse(notification);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getMessage()).isEqualTo("Welcome to FounderLink!");
        assertThat(response.getType()).isEqualTo(NotificationType.USER_REGISTERED);
        assertThat(response.getIsRead()).isFalse();
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toResponse_whenRead_shouldReturnTrue() {
        Notification notification = Notification.builder()
                .id(1L).userId(10L).message("Test")
                .type(NotificationType.INVESTMENT_APPROVED)
                .isRead(true)
                .build();

        NotificationResponse response = mapper.toResponse(notification);

        assertThat(response.getIsRead()).isTrue();
    }
}
