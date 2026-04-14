package com.capgemini.notification.service.impl;

import com.capgemini.notification.dto.NotificationResponse;
import com.capgemini.notification.entity.Notification;
import com.capgemini.notification.enums.NotificationType;
import com.capgemini.notification.mapper.NotificationMapper;
import com.capgemini.notification.repository.NotificationRepository;
import com.capgemini.notification.service.NotificationCommandService;
import com.capgemini.notification.service.NotificationQueryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import jakarta.persistence.EntityNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationServiceImpl implements NotificationCommandService, NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    public NotificationServiceImpl(NotificationRepository notificationRepository, NotificationMapper notificationMapper) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "notifications", key = "#userId"),
            @CacheEvict(value = "unreadNotifications", key = "#userId")
    })
    public void createNotification(Long userId, String message, NotificationType type) {
        notificationRepository.save(
                Notification.builder()
                        .userId(userId)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .build()
        );
    }

    @Override
    // Boosts read speeds for notification trays using the user's ID as the cache key.
    @Cacheable(value = "notifications", key = "#userId")
    public List<NotificationResponse> getNotificationsByUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(notificationMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "unreadNotifications", key = "#userId")
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(notificationMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "notifications", allEntries = true),
            @CacheEvict(value = "unreadNotifications", allEntries = true)
    })
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.setIsRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

}
