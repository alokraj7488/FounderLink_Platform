package com.capgemini.notification.service;

import com.capgemini.notification.dto.NotificationResponse;
import java.util.List;

// Serves up notification reads quickly by hitting the Redis cache first.
public interface NotificationQueryService {
    List<NotificationResponse> getNotificationsByUser(Long userId);
    List<NotificationResponse> getUnreadNotifications(Long userId);
}
