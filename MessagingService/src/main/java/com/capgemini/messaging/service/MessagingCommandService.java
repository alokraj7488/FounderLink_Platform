package com.capgemini.messaging.service;

import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageRequest;
import com.capgemini.messaging.dto.MessageResponse;

// Handles write operations for messaging. Triggers cache evictions to keep data fresh.
public interface MessagingCommandService {
    MessageResponse sendMessage(Long senderId, MessageRequest request);
    ConversationResponse getOrCreateConversation(Long userId, Long otherUserId);
}
