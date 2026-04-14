package com.capgemini.user.listener;

import com.capgemini.user.event.UserRegisteredEvent;
import com.capgemini.user.entity.UserProfile;
import com.capgemini.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredListener {

    private final UserProfileRepository userProfileRepository;

    @RabbitListener(queues = "${rabbitmq.queue.user-registered}")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent — Creating base profile for userId: {}", event.getUserId());

        if (userProfileRepository.existsByUserId(event.getUserId())) {
            log.warn("Profile already exists for userId: {}, skipping base profile creation", event.getUserId());
            return;
        }

        UserProfile profile = UserProfile.builder()
                .userId(event.getUserId())
                .name(event.getName())
                .email(event.getEmail())
                .role(event.getRole())
                .bio("")
                .skills("")
                .experience("")
                .build();

        userProfileRepository.save(profile);
        log.info("Base profile created successfully for userId: {}", event.getUserId());
    }
}
