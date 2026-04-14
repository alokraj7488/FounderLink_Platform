package com.capgemini.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class MessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String content;
    private LocalDateTime createdAt;
}
