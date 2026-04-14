package com.capgemini.messaging.service.impl;

import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageRequest;
import com.capgemini.messaging.dto.MessageResponse;
import com.capgemini.messaging.entity.Conversation;
import com.capgemini.messaging.entity.Message;
import com.capgemini.messaging.repository.ConversationRepository;
import com.capgemini.messaging.repository.MessageRepository;
import com.capgemini.messaging.mapper.MessageMapper;
import com.capgemini.messaging.service.MessagingCommandService;
import com.capgemini.messaging.service.MessagingQueryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessagingServiceImpl implements MessagingCommandService, MessagingQueryService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageMapper messageMapper;

    public MessagingServiceImpl(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                SimpMessagingTemplate messagingTemplate,
                                MessageMapper messageMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messagingTemplate = messagingTemplate;
        this.messageMapper = messageMapper;
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "conversationMessages", allEntries = true),
            @CacheEvict(value = "userConversations", allEntries = true)
    })
    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        Conversation conversation = conversationRepository
                .findByParticipants(senderId, request.getReceiverId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .participant1Id(senderId)
                                .participant2Id(request.getReceiverId())
                                .build()
                ));

        Message message = messageRepository.save(
                Message.builder()
                        .conversationId(conversation.getId())
                        .senderId(senderId)
                        .content(request.getContent())
                        .build()
        );

        MessageResponse response = messageMapper.toMessageResponse(message);

        // Broadcast to both participants subscribed on /topic/conversation/{id}
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(),
                response
        );

        return response;
    }

    @Override
    // Quickly pulls messages from memory to keep chats blazing fast.
    @Cacheable(value = "conversationMessages", key = "#conversationId")
    public List<MessageResponse> getConversationMessages(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!conversation.getParticipant1Id().equals(userId) && !conversation.getParticipant2Id().equals(userId)) {
            throw new RuntimeException("Access denied: You are not a participant in this conversation");
        }

        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(messageMapper::toMessageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "userConversations", key = "#userId")
    public List<ConversationResponse> getMyConversations(Long userId) {
        return conversationRepository.findAllByUserId(userId)
                .stream()
                .map(c -> messageMapper.toConversationResponse(c, List.of()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "conversationMessages", allEntries = true),
            @CacheEvict(value = "userConversations", allEntries = true)
    })
    public ConversationResponse getOrCreateConversation(Long userId, Long otherUserId) {
        Conversation conversation = conversationRepository
                .findByParticipants(userId, otherUserId)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .participant1Id(userId)
                                .participant2Id(otherUserId)
                                .build()
                ));
        List<MessageResponse> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversation.getId())
                .stream().map(messageMapper::toMessageResponse).collect(Collectors.toList());
        return messageMapper.toConversationResponse(conversation, messages);
    }

}
