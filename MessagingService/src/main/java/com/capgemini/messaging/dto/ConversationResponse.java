package com.capgemini.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ConversationResponse {
    private Long id;
    private Long participant1Id;
    private Long participant2Id;
    private LocalDateTime createdAt;
    private List<MessageResponse> messages;
}
