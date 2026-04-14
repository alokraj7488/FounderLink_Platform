# Messaging Service — Interview Preparation Guide

---

## What does Messaging Service do?

The Messaging Service provides **real-time direct messaging** between users on FounderLink. Investors can message founders, founders can message potential co-founders, etc.

**Two communication channels:**
1. **REST API** — For fetching conversation history and creating conversations
2. **WebSocket (STOMP)** — For real-time message delivery without polling

---

## Key Concepts to Know

**WebSocket** — A full-duplex communication protocol. Unlike HTTP (request → response, connection closed), WebSocket keeps the connection open and allows both server and client to send messages at any time. Used for real-time features like chat, notifications, live updates.

**STOMP (Simple Text Oriented Messaging Protocol)** — A protocol on top of WebSocket that adds concepts like destinations (like channels/topics), subscriptions, and acknowledgments. Think of it as "HTTP for WebSocket."

**SockJS** — A fallback library. If the browser doesn't support WebSocket (older browsers), SockJS falls back to long-polling. Ensures compatibility.

---

## Architecture

```
[User connects via WebSocket to ws://messaging-service/ws]
    ↓ WebSocketAuthInterceptor validates JWT on CONNECT frame
    ↓ STOMP session established
    
[User A sends message: STOMP SEND /app/chat]
    → MessageController @MessageMapping("/chat")
    → MessagingServiceImpl.sendMessage()
    → Saves to DB
    → SimpMessagingTemplate.convertAndSend("/topic/conversation/1", message)
    → WebSocket broker broadcasts to ALL subscribers of /topic/conversation/1
    → User B (subscribed to /topic/conversation/1) receives the message in real-time

[User B opens conversation history: GET /messages/conversation/1]
    → MessageController REST endpoint
    → MessagingServiceImpl.getConversationMessages(1)
    → Returns list of messages from DB
```

---

## File 1: WebSocketConfig.java

```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker handles /topic destinations
        config.enableSimpleBroker("/topic");
        // Client sends to /app/... which routes to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Validate JWT on every STOMP CONNECT frame
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

**`@EnableWebSocketMessageBroker`**
- Enables Spring's WebSocket message handling with a full-featured message broker.
- Adds support for STOMP protocol over WebSocket.

**`configureMessageBroker`**
- `enableSimpleBroker("/topic")` — activates an in-memory message broker for destinations starting with `/topic`.
  - "Simple broker" means it's a basic in-memory broker built into Spring. For production with many instances, you'd use a full message broker like RabbitMQ as the WebSocket broker.
  - When any code sends to `/topic/conversation/1`, the broker delivers it to all clients subscribed to that destination.
- `setApplicationDestinationPrefixes("/app")` — messages sent by clients to `/app/...` are routed to `@MessageMapping` methods (your application code), NOT directly to the broker.

**`registerStompEndpoints`**
- `.addEndpoint("/ws")` — WebSocket handshake URL: `ws://localhost:8086/ws`
- `.setAllowedOriginPatterns("*")` — Allow WebSocket connections from any origin (similar to CORS for WebSockets).
- `.withSockJS()` — Enable SockJS fallback for browsers that don't support WebSocket.

**`configureClientInboundChannel`**
- Registers `webSocketAuthInterceptor` as a channel interceptor.
- This interceptor runs on every message received from clients — including the initial STOMP CONNECT frame.
- It validates the JWT in the CONNECT frame's `Authorization` header.
- If JWT is invalid → the connection is rejected before establishing the STOMP session.

---

## File 2: WebSocketAuthInterceptor.java

```java
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            // Validate JWT and set authentication in session
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                // Validate token, extract userId and roles
                // Set accessor.setUser(authentication)
            }
        }
        return message;
    }
}
```

**`ChannelInterceptor`** — Interface for intercepting messages in Spring WebSocket's message channels.

**`preSend(message, channel)`** — Called BEFORE each message is delivered to the channel. Return null to block the message.

**`StompHeaderAccessor`** — Accessor class for reading/writing STOMP-specific headers from a Spring WebSocket message.

**`StompCommand.CONNECT`** — The CONNECT command is the first message a client sends to establish a STOMP session. This is where we validate authentication.

**Why authenticate on CONNECT instead of on every message?**
- The CONNECT frame establishes the session. Once authenticated, the session is trusted for all subsequent messages.
- This avoids validating the JWT on every single message (which would be expensive).
- HTTP requests are stateless; WebSocket sessions are stateful.

---

## File 3: MessageController.java

```java
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @RequestHeader("X-User-Id") Long senderId,
            @Valid @RequestBody MessageRequest request) {
        MessageResponse response = messagingService.sendMessage(senderId, request);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully", response));
    }
```

**`@RequestHeader("X-User-Id") Long senderId`**
- Reads the `X-User-Id` header injected by the API Gateway.
- This is how the controller gets the authenticated user's ID without parsing the JWT itself.
- No `Authentication` object injection needed — the header is trusted.

```java
    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam Long otherUserId) {
        ConversationResponse response = messagingService.getOrCreateConversation(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success("Conversation ready", response));
    }
```

`getOrCreateConversation` uses the `orElseGet` pattern — if the conversation exists, return it; if not, create it. This is idempotent — calling it multiple times doesn't create duplicate conversations.

---

## File 4: MessagingServiceImpl.java

```java
@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageMapper messageMapper;

    @Override
    @Transactional
    public MessageResponse sendMessage(Long senderId, MessageRequest request) {
        // Get or create conversation
        Conversation conversation = conversationRepository
                .findByParticipants(senderId, request.getReceiverId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .participant1Id(senderId)
                                .participant2Id(request.getReceiverId())
                                .build()
                ));

        // Save message to database
        Message message = messageRepository.save(
                Message.builder()
                        .conversationId(conversation.getId())
                        .senderId(senderId)
                        .content(request.getContent())
                        .build()
        );

        MessageResponse response = messageMapper.toMessageResponse(message);

        // Broadcast via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversation.getId(),
                response
        );

        return response;
    }
```

**`SimpMessagingTemplate`**
- The Spring class for sending messages to WebSocket clients.
- `SimpMessagingTemplate.convertAndSend(destination, payload)` — sends `payload` (serialized as JSON) to all WebSocket clients subscribed to `destination`.
- `destination = "/topic/conversation/1"` — any client that did `stompClient.subscribe("/topic/conversation/1", callback)` receives this message in real-time.

**`orElseGet(() -> conversationRepository.save(...))`**
- `orElseGet` is like `orElse` but the value is computed lazily — only if the Optional is empty.
- `orElse(new Object())` would construct the object even if the Optional has a value (wasteful).
- `orElseGet(() -> ...)` only creates the conversation if one doesn't already exist.

**What happens when message is sent:**
1. Find or create the conversation between sender and receiver.
2. Save the message to PostgreSQL (permanent storage).
3. Use `SimpMessagingTemplate` to push the message to `/topic/conversation/{id}`.
4. The WebSocket broker delivers it to all subscribers (sender's and receiver's browser tabs).
5. Return the message response to the REST caller (the HTTP response).

---

## File 5: Message.java (Entity)

```java
@Entity
@Table(name = "messages")
public class Message {

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

Message content uses `TEXT` type (unlimited length in PostgreSQL).

`@PrePersist` sets `createdAt` automatically.

`updatable = false` — once set, `createdAt` cannot be changed. This preserves the original send time.

---

## File 6: Conversation.java (Entity)

```java
@Entity
@Table(name = "conversations")
public class Conversation {
    private Long participant1Id;
    private Long participant2Id;
    ...
}
```

**ConversationRepository has a custom query:**
```java
@Query("SELECT c FROM Conversation c WHERE (c.participant1Id = :user1 AND c.participant2Id = :user2) " +
       "OR (c.participant1Id = :user2 AND c.participant2Id = :user1)")
Optional<Conversation> findByParticipants(@Param("user1") Long user1, @Param("user2") Long user2);
```

This query finds the conversation between two users regardless of which one is `participant1` and which is `participant2`. The `OR` condition handles both orderings.

---

## How Real-Time Chat Works End-to-End

```
Frontend (React) setup:
1. Connect: const stompClient = new StompClient({brokerURL: 'ws://localhost:8080/ws'})
2. On connect: stompClient.subscribe('/topic/conversation/1', (frame) => {
       const message = JSON.parse(frame.body);
       addMessageToUI(message);
   })

Sending a message:
3. User types "Hello" and clicks Send
4. Frontend calls: POST /messages {receiverId: 2, content: "Hello"}
5. MessagingService saves message to DB
6. MessagingService calls messagingTemplate.convertAndSend("/topic/conversation/1", messageResponse)
7. WebSocket broker pushes JSON to all subscribers of /topic/conversation/1
8. Receiver's browser receives the message in callback (step 2)
9. UI updates without page refresh
```

---

## Interview Q&A

**Q: What is WebSocket and how is it different from HTTP?**

HTTP is a request-response protocol. The client sends a request, the server responds, and the connection closes. For real-time features (chat, notifications), the client would need to repeatedly poll the server (e.g., every second: "any new messages?") — inefficient and laggy. WebSocket establishes a persistent, full-duplex connection. Both the client and server can send messages at any time without a request-response cycle. This is ideal for chat: when User B sends a message, the server pushes it directly to User A's browser instantly.

**Q: What is STOMP and why is it used on top of WebSocket?**

WebSocket is a low-level protocol — it just transfers raw bytes. STOMP adds structure: the concept of "destinations" (like `/topic/conversation/1`), "subscriptions" (clients say "I want messages for this destination"), and "sends" (clients send to a destination). Without STOMP, you'd need to implement your own routing and subscription management. STOMP turns WebSocket into a message-broker-compatible protocol, allowing Spring's `SimpMessagingTemplate` to route messages to the right subscribers.

**Q: What is SockJS?**

Some corporate networks, firewalls, or older browsers block WebSocket connections. SockJS is a JavaScript library that provides a WebSocket-like API but with fallback transport mechanisms. If WebSocket is blocked, it falls back to HTTP long-polling (the server holds the request open until a message arrives). The client code doesn't change — SockJS handles the transport transparently.

**Q: How is authentication handled for WebSocket connections?**

HTTP requests carry the JWT in the `Authorization: Bearer <token>` header. WebSocket connections don't have traditional headers after the initial handshake. We use `WebSocketAuthInterceptor` which implements `ChannelInterceptor`. When the client sends a STOMP CONNECT frame, it includes the JWT in a custom header. The interceptor validates this JWT and sets the authentication in the STOMP session. All subsequent messages in the session are trusted as authenticated by that user.

**Q: What happens if two users chat for the first time?**

`conversationRepository.findByParticipants(user1Id, user2Id)` returns `Optional.empty()`. `orElseGet()` runs the lambda which creates and saves a new `Conversation` record. On subsequent messages between the same users, the conversation record is found and reused. This ensures there's exactly ONE conversation between any two users — no duplicates.
