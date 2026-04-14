package com.capgemini.messaging.service.impl;

import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageRequest;
import com.capgemini.messaging.dto.MessageResponse;
import com.capgemini.messaging.entity.Conversation;
import com.capgemini.messaging.entity.Message;
import com.capgemini.messaging.mapper.MessageMapper;
import com.capgemini.messaging.repository.ConversationRepository;
import com.capgemini.messaging.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagingServiceImplTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private MessageMapper messageMapper;

    @InjectMocks
    private MessagingServiceImpl service;

    private Conversation conversation;
    private Message message;
    private MessageResponse messageResponse;

    @BeforeEach
    void setUp() {
        conversation = Conversation.builder().id(10L).participant1Id(1L).participant2Id(2L).build();
        message = Message.builder().id(1L).conversationId(10L).senderId(1L).content("Hello").build();
        messageResponse = MessageResponse.builder().id(1L).conversationId(10L).senderId(1L).content("Hello").build();
    }

    @Test
    void sendMessage_whenConversationExists_shouldSendAndBroadcast() {
        MessageRequest request = new MessageRequest();
        request.setReceiverId(2L);
        request.setContent("Hello");

        when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenReturn(message);
        when(messageMapper.toMessageResponse(message)).thenReturn(messageResponse);

        MessageResponse result = service.sendMessage(1L, request);

        assertThat(result.getContent()).isEqualTo("Hello");
        verify(messagingTemplate).convertAndSend(eq("/topic/conversation/10"), any(MessageResponse.class));
    }

    @Test
    void sendMessage_whenNoConversation_shouldCreateNewConversation() {
        MessageRequest request = new MessageRequest();
        request.setReceiverId(2L);
        request.setContent("Hi");

        when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenReturn(conversation);
        when(messageRepository.save(any())).thenReturn(message);
        when(messageMapper.toMessageResponse(message)).thenReturn(messageResponse);

        MessageResponse result = service.sendMessage(1L, request);

        assertThat(result).isNotNull();
        verify(conversationRepository).save(any());
    }

    @Test
    void getConversationMessages_shouldReturnMappedMessages() {
        when(conversationRepository.findById(10L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(message));
        when(messageMapper.toMessageResponse(message)).thenReturn(messageResponse);

        List<MessageResponse> result = service.getConversationMessages(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("Hello");
    }

    @Test
    void getMyConversations_shouldReturnUserConversations() {
        ConversationResponse convResponse = ConversationResponse.builder().id(10L).build();
        when(conversationRepository.findAllByUserId(1L)).thenReturn(List.of(conversation));
        when(messageMapper.toConversationResponse(eq(conversation), any())).thenReturn(convResponse);

        List<ConversationResponse> result = service.getMyConversations(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void getOrCreateConversation_whenExists_shouldReturnWithMessages() {
        when(conversationRepository.findByParticipants(1L, 2L)).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(message));
        when(messageMapper.toMessageResponse(message)).thenReturn(messageResponse);
        ConversationResponse convResponse = ConversationResponse.builder().id(10L).build();
        when(messageMapper.toConversationResponse(eq(conversation), any())).thenReturn(convResponse);

        ConversationResponse result = service.getOrCreateConversation(1L, 2L);

        assertThat(result.getId()).isEqualTo(10L);
    }
}
