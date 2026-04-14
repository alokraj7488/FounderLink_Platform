# Notification Service — Interview Preparation Guide

---

## What does Notification Service do?

The Notification Service is the **event consumer hub** of FounderLink. It listens to RabbitMQ events published by OTHER services and creates in-app notifications + sends emails.

**It does NOT receive direct API calls to create notifications** (mostly) — it reacts to events happening in the system:
- "A startup was created" → notify the founder
- "An investment was made" → notify the founder
- "An investment was approved" → notify the investor
- "A team invite was sent" → notify the invited user
- "A startup was rejected" → notify the founder
- "Payment succeeded" → notify both parties
- "Payment rejected" → notify both parties

Additionally, it provides a **real-time WebSocket push** — when a notification is created, it's immediately pushed to the user's browser via WebSocket (if they're connected).

---

## Architecture

```
[StartupService publishes startup.created]
[InvestmentService publishes investment.created]
[TeamService publishes team.invite.sent]
[PaymentService publishes payment.success / payment.failed]
[AuthService publishes user.registered]
         ↓ all to founderlink.exchange (TopicExchange)
         ↓
[RabbitMQ — queues bound by routing key]
         ↓
[NotificationEventListener — @RabbitListener methods]
         ↓
[NotificationServiceImpl.createNotification()]
         ├── Save to PostgreSQL (Notification entity)
         └── SimpMessagingTemplate.convertAndSend("/topic/notifications/{userId}")
              ↓
         [User's browser receives notification in real-time via WebSocket]

[Client: GET /notifications/{userId}]
         ↓
[NotificationController → NotificationService.getNotificationsByUser()]
         → Returns list of notifications from DB
```

---

## File 1: NotificationEventListener.java

This is the heart of the service — it listens to 8 different RabbitMQ queues.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    @RabbitListener(queues = "${rabbitmq.queue.startup-created}")
    public void handleStartupCreated(StartupCreatedEvent event) {
        log.info("Received startup created event for startupId: {}", event.getStartupId());
        notificationService.createNotification(
                event.getFounderId(),
                "Your startup has been submitted for review. Startup ID: " + event.getStartupId(),
                NotificationType.STARTUP_CREATED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.investment-created}")
    public void handleInvestmentCreated(InvestmentCreatedEvent event) {
        notificationService.createNotification(
                event.getFounderId(),   // Notify the FOUNDER
                "New investment request of amount " + event.getAmount() + " received for your startup.",
                NotificationType.INVESTMENT_CREATED
        );
    }

    @RabbitListener(queues = "${rabbitmq.queue.investment-approved}")
    public void handleInvestmentApproved(InvestmentApprovedEvent event) {
        notificationService.createNotification(
                event.getInvestorId(),  // Notify the INVESTOR
                "Your investment of amount " + event.getAmount() + " has been approved.",
                NotificationType.INVESTMENT_APPROVED
        );
    }
```

**`@RabbitListener(queues = "${rabbitmq.queue.startup-created}")`**
- Marks this method as a RabbitMQ message consumer.
- `queues` — the name of the queue to listen to (read from config: e.g., `startup.created.queue`).
- Spring automatically deserializes the message JSON into the Java object type (e.g., `StartupCreatedEvent`).
- Runs in a background thread — when a message arrives in the queue, Spring calls this method.
- If the method throws an exception, the message may be requeued (depending on configuration).

**Message deserialization:**
- `Jackson2JsonMessageConverter` (configured in `RabbitMQConfig`) converts the JSON message body to the Java type of the method parameter.
- For complex event types, the Java class must match the JSON structure of the publisher's event object.
- For generic payloads (like payment events where the publisher class might differ), we use `Map<String, Object>` to avoid `ClassNotFoundException`:

```java
@RabbitListener(queues = "${rabbitmq.queue.payment-failed}")
public void handlePaymentFailed(Map<String, Object> payload) {
    try {
        Long investorId = payload.get("investorId") != null
                ? ((Number) payload.get("investorId")).longValue() : null;
        String startupName = (String) payload.get("startupName");
        ...
    } catch (Exception e) {
        log.error("Failed to process payment rejected event: {}", e.getMessage());
    }
}
```

**Why `Map<String, Object>` instead of a typed class for payment events?**
- PaymentService has its own `PaymentEventDTO` class with a different package path.
- When RabbitMQ uses Jackson for deserialization, it includes the fully-qualified class name in the message header (`__TypeId__`).
- If NotificationService doesn't have the same class at the same package path, deserialization fails with `ClassNotFoundException`.
- Using `Map<String, Object>` bypasses this — we deserialize to a generic map and extract fields manually.
- This is a common real-world pattern when services evolve independently.

```java
@RabbitListener(queues = "${rabbitmq.queue.user-registered}")
public void handleUserRegistered(Map<String, Object> payload) {
    try {
        Long userId   = payload.get("userId") != null ? ((Number) payload.get("userId")).longValue() : null;
        String name   = (String) payload.get("name");
        String role   = (String) payload.get("role");

        if (userId != null) {
            notificationService.createNotification(
                    userId,
                    "Welcome to FounderLink, " + name + "! Your " + formatRole(role) + " profile has been created successfully.",
                    NotificationType.USER_REGISTERED
            );
        }
    } catch (Exception e) {
        log.error("Failed to process user.registered event: {}", e.getMessage());
    }
}
```

The entire method is wrapped in try-catch. **Why?** Because if processing fails, we don't want the message to be requeued and retried infinitely. We log the error and move on. This is called a "dead letter" pattern — in production, you'd route failed messages to a Dead Letter Queue (DLQ) for investigation.

---

## File 2: NotificationServiceImpl.java

```java
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public void createNotification(Long userId, String message, NotificationType type) {
        Notification notification = notificationRepository.save(
                Notification.builder()
                        .userId(userId)
                        .message(message)
                        .type(type)
                        .isRead(false)
                        .build()
        );

        // Push real-time notification via WebSocket
        messagingTemplate.convertAndSend(
                "/topic/notifications/" + userId,
                notificationMapper.toResponse(notification)
        );
    }

    @Override
    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(notificationMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.setIsRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }
}
```

**`SimpMessagingTemplate.convertAndSend("/topic/notifications/" + userId, ...)`**
- Pushes the notification to the WebSocket topic for this specific user.
- If the user is connected and subscribed to `/topic/notifications/42`, they receive the notification instantly.
- If not connected — no problem. The notification is already saved in the database. They'll see it when they call `GET /notifications/{userId}`.

**`findByUserIdAndIsReadFalseOrderByCreatedAtDesc`**
- This is a Spring Data JPA **derived query method**.
- Spring parses the method name and generates the SQL:
  - `findBy` → `SELECT ...`
  - `UserId` → `WHERE user_id = ?`
  - `AndIsReadFalse` → `AND is_read = false`
  - `OrderByCreatedAtDesc` → `ORDER BY created_at DESC`
- No need to write JPQL or native SQL for this query.

**`markAsRead`**
- Sets `isRead = true` for a notification.
- Used when the user opens their notification panel — the frontend calls this to mark notifications as read.
- The unread count on the UI badge is updated accordingly.

---

## File 3: RabbitMQConfig.java (Complex Consumer Config)

```java
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queue.investment-created}")
    private String investmentCreatedQueue;
    // ... 7 more @Value fields for each queue

    @Bean
    public TopicExchange founderLinkExchange() {
        return new TopicExchange(exchange);
    }

    @Bean
    public Queue investmentCreatedQueue() {
        return QueueBuilder.durable(investmentCreatedQueue).build();
    }

    @Bean
    public Binding investmentCreatedBinding() {
        return BindingBuilder.bind(investmentCreatedQueue())
                .to(founderLinkExchange())
                .with("investment.created");
    }

    // ... same pattern for all 8 queues
}
```

NotificationService is the biggest consumer — it listens to 8 queues:
1. `user.registered.queue` → routing key `user.registered`
2. `startup.created.queue` → routing key `startup.created`
3. `startup.rejected.queue` → routing key `startup.rejected`
4. `investment.created.queue` → routing key `investment.created`
5. `investment.approved.queue` → routing key `investment.approved`
6. `team.invite.sent.queue` → routing key `team.invite.sent`
7. `payment.success.queue` → routing key `payment.success`
8. `payment.failed.queue` → routing key `payment.failed`

**Important:** Each service that publishes events also declares these queues (using `@Bean Queue`). RabbitMQ is idempotent for queue/exchange declarations — if a queue/exchange already exists with the same configuration, re-declaring it does nothing.

---

## File 4: Notification.java (Entity)

```java
@Entity
@Table(name = "notifications")
public class Notification {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Builder.Default
    private Boolean isRead = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

**NotificationType enum:**
```java
public enum NotificationType {
    USER_REGISTERED,
    STARTUP_CREATED,
    STARTUP_REJECTED,
    INVESTMENT_CREATED,
    INVESTMENT_APPROVED,
    TEAM_INVITE_SENT,
    PAYMENT_SUCCESS,
    PAYMENT_REJECTED
}
```

These types allow the frontend to display different icons/colors for different notification types.

---

## File 5: WebSocket + Security Configuration

NotificationService also has:
- `WebSocketConfig.java` — same pattern as MessagingService (simple broker + STOMP endpoint `/ws`)
- `WebSocketAuthInterceptor.java` — validates JWT on STOMP CONNECT
- `SecurityConfig.java` — uses `JwtAuthenticationFilter` with `@EnableMethodSecurity`

The user subscribes to `/topic/notifications/{userId}` on the frontend:
```javascript
stompClient.subscribe('/topic/notifications/42', (frame) => {
    const notification = JSON.parse(frame.body);
    showNotificationBadge(notification);
});
```

---

## Complete Notification Flow Example

**Scenario:** Investor (userId=5) invests ₹500,000 in TechCorp (startupId=1, founderId=2)

```
1. InvestmentService saves Investment {investorId=5, startupId=1, amount=500000}
2. InvestmentService publishes InvestmentCreatedEvent {
       investorId=5, startupId=1, founderId=2, amount=500000
   } to founderlink.exchange with routing key "investment.created"

3. RabbitMQ delivers message to investment.created.queue

4. NotificationEventListener.handleInvestmentCreated(event) runs
5. notificationService.createNotification(
       userId=2,  ← the FOUNDER is notified
       "New investment request of amount 500000 received",
       INVESTMENT_CREATED
   )

6. Notification saved to DB: {userId=2, message="...", type=INVESTMENT_CREATED, isRead=false}

7. messagingTemplate.convertAndSend("/topic/notifications/2", notificationResponse)
8. Founder's browser (subscribed to /topic/notifications/2) receives the notification instantly
9. Frontend shows notification badge: "1 new notification"
```

---

## Interview Q&A

**Q: How does the event-driven notification system work?**

Other microservices publish domain events to RabbitMQ (e.g., InvestmentService publishes `investment.created`). NotificationService has `@RabbitListener` methods that consume these events from dedicated queues. Each listener creates an in-app notification in the database and pushes a real-time WebSocket notification to the affected user. This is an event-driven architecture — NotificationService is completely decoupled from the other services. Adding a new notification type only requires adding a new queue/listener, without modifying the publishing service.

**Q: Why do some listeners use `Map<String, Object>` instead of typed event classes?**

When RabbitMQ uses Jackson's `Jackson2JsonMessageConverter`, messages include a `__TypeId__` header with the fully-qualified Java class name. If the consumer doesn't have that exact class at that exact package path, deserialization fails. For payment events from PaymentService, the class package (`com.capgemini.payment.dto.PaymentEventDTO`) doesn't exist in NotificationService. Using `Map<String, Object>` bypasses the type-ID check and deserializes to a generic map. We then manually extract fields with safe null-checks.

**Q: What is the difference between in-app notifications and email notifications?**

In-app notifications are stored in the database (`Notification` entity) and pushed via WebSocket. They appear in the user's notification panel within the app. Email notifications are sent via `EmailService` (using Spring JavaMail) to the user's email address. FounderLink uses both: in-app for immediate visibility when the user is logged in, and email for when they're offline.

**Q: What is `@Transactional` and why is it on `createNotification`?**

`@Transactional` ensures that if saving the notification to the database succeeds but the WebSocket push fails, the database save is NOT rolled back. The notification is still stored for the user to see when they next visit. Actually, in this case `@Transactional` mainly ensures the save operation is atomic. The WebSocket push happens after the save, so if the push fails (user disconnected), the saved notification remains intact.

**Q: How are unread notification counts maintained?**

When a notification is created, `isRead = false` by default. The `getUnreadNotifications` method queries `WHERE user_id = ? AND is_read = false`. The frontend calls this on page load to count unread notifications for the badge. When the user opens the notification panel, the frontend calls `PUT /notifications/{id}/read` which calls `markAsRead()`, setting `isRead = true`. The unread count badge updates accordingly.
