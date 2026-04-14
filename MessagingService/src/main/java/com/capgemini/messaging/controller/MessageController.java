package com.capgemini.messaging.controller;

import com.capgemini.messaging.dto.ApiResponse;
import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageRequest;
import com.capgemini.messaging.dto.MessageResponse;
import com.capgemini.messaging.service.MessagingCommandService;
import com.capgemini.messaging.service.MessagingQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessagingCommandService messagingCommandService;
    private final MessagingQueryService messagingQueryService;

    public MessageController(MessagingCommandService messagingCommandService, MessagingQueryService messagingQueryService) {
        this.messagingCommandService = messagingCommandService;
        this.messagingQueryService = messagingQueryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @RequestHeader("X-User-Id") Long senderId,
            @Valid @RequestBody MessageRequest request) {
        MessageResponse response = messagingCommandService.sendMessage(senderId, request);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", response));
    }

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getConversationMessages(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long conversationId) {
        List<MessageResponse> messages = messagingQueryService.getConversationMessages(conversationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Messages fetched successfully", messages));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getMyConversations(
            @RequestHeader("X-User-Id") Long userId) {
        List<ConversationResponse> conversations = messagingQueryService.getMyConversations(userId);
        return ResponseEntity.ok(ApiResponse.success("Conversations fetched successfully", conversations));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long otherUserId) {
        ConversationResponse response = messagingCommandService.getOrCreateConversation(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success("Conversation ready", response));
    }
}
