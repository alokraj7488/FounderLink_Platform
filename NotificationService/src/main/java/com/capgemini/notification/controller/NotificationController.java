package com.capgemini.notification.controller;

import com.capgemini.notification.dto.ApiResponse;
import com.capgemini.notification.dto.NotificationResponse;
import com.capgemini.notification.service.NotificationCommandService;
import com.capgemini.notification.service.NotificationQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationCommandService notificationCommandService;
    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationCommandService notificationCommandService, NotificationQueryService notificationQueryService) {
        this.notificationCommandService = notificationCommandService;
        this.notificationQueryService = notificationQueryService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @PathVariable Long userId) {
        List<NotificationResponse> notifications = notificationQueryService.getNotificationsByUser(userId);
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully", notifications));
    }

    @GetMapping("/{userId}/unread")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnreadNotifications(
            @PathVariable Long userId) {
        List<NotificationResponse> notifications = notificationQueryService.getUnreadNotifications(userId);
        return ResponseEntity.ok(ApiResponse.success("Unread notifications fetched", notifications));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable Long notificationId) {
        NotificationResponse response = notificationCommandService.markAsRead(notificationId);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }
}
