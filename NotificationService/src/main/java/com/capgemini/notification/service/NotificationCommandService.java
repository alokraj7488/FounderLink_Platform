package com.capgemini.notification.service;

import com.capgemini.notification.dto.NotificationResponse;
import com.capgemini.notification.enums.NotificationType;

// Handles all write operations for notifications and ensures stale caches are evicted.
public interface NotificationCommandService {
    void createNotification(Long userId, String message, NotificationType type);
    NotificationResponse markAsRead(Long notificationId);
}
