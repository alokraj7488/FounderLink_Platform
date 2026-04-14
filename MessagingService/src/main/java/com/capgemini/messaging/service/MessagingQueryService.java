package com.capgemini.messaging.service;

import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageResponse;
import java.util.List;

// Handles read operations for messaging. Uses Redis cache to serve data blazingly fast.
public interface MessagingQueryService {
    List<MessageResponse> getConversationMessages(Long conversationId, Long userId);
    List<ConversationResponse> getMyConversations(Long userId);
}
