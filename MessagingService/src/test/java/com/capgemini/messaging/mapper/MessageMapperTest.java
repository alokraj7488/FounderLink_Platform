package com.capgemini.messaging.mapper;

import com.capgemini.messaging.dto.ConversationResponse;
import com.capgemini.messaging.dto.MessageResponse;
import com.capgemini.messaging.entity.Conversation;
import com.capgemini.messaging.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTest {

    private MessageMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MessageMapper();
    }

    @Test
    void toMessageResponse_shouldMapAllFields() {
        LocalDateTime now = LocalDateTime.now();
        Message message = Message.builder()
                .id(1L).conversationId(10L).senderId(100L)
                .content("Hello!").createdAt(now)
                .build();

        MessageResponse response = mapper.toMessageResponse(message);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getConversationId()).isEqualTo(10L);
        assertThat(response.getSenderId()).isEqualTo(100L);
        assertThat(response.getContent()).isEqualTo("Hello!");
        assertThat(response.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toConversationResponse_shouldMapFieldsAndIncludeMessages() {
        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = Conversation.builder()
                .id(10L).participant1Id(1L).participant2Id(2L).createdAt(now)
                .build();

        MessageResponse msg1 = MessageResponse.builder().id(1L).content("Hi").build();
        MessageResponse msg2 = MessageResponse.builder().id(2L).content("Hello").build();

        ConversationResponse response = mapper.toConversationResponse(conversation, List.of(msg1, msg2));

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getParticipant1Id()).isEqualTo(1L);
        assertThat(response.getParticipant2Id()).isEqualTo(2L);
        assertThat(response.getMessages()).hasSize(2);
    }

    @Test
    void toConversationResponse_withEmptyMessages_shouldReturnEmptyList() {
        Conversation conversation = Conversation.builder()
                .id(10L).participant1Id(1L).participant2Id(2L).build();

        ConversationResponse response = mapper.toConversationResponse(conversation, List.of());

        assertThat(response.getMessages()).isEmpty();
    }
}
