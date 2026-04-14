package com.capgemini.notification.dto;

import com.capgemini.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationResponse {
    private Long id;
    private Long userId;
    private String message;
    private NotificationType type;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
