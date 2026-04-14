# Payment Service — Interview Preparation Guide

---

## What does Payment Service do?

The Payment Service handles **actual money transactions** using the Razorpay payment gateway. When an investment is approved, the investor makes a real payment via Razorpay. The service also implements a **Saga Pattern** to manage the distributed transaction across services.

**Payment Flow:**
1. Investor creates a Razorpay order → gets order ID
2. Investor completes payment on Razorpay's UI → gets paymentId + signature
3. Service verifies the signature (prevents tampering)
4. Payment waits for **founder's explicit approval** (AWAITING_APPROVAL)
5. Founder approves → payment SUCCESS, notifications sent, emails sent
6. Founder rejects → **compensation**: refund issued via Razorpay API, notifications sent

This is a multi-step distributed transaction — the **Saga Pattern** tracks each step.

---

## What is Razorpay?

Razorpay is a payment gateway (like Stripe for India) that:
- Accepts UPI, cards, net banking, wallets
- Provides a JavaScript SDK for the payment UI
- Returns a signed response that your backend must verify

**NEVER trust the frontend's claim that payment was successful.** Always verify the Razorpay signature on the backend. This is the most critical security step.

---

## What is the Saga Pattern?

A **Saga** is a pattern for managing distributed transactions that span multiple services. In a traditional monolith, you'd use a single database transaction. In microservices, you can't have a cross-service database transaction.

**The problem:** When a founder rejects a payment:
1. Update payment status to REJECTED in DB
2. Refund investor via Razorpay API
3. Publish payment.failed event → NotificationService notifies both parties
4. Send email to investor

These steps involve the database, Razorpay API, RabbitMQ, and email. If step 3 fails, steps 1 and 2 are done but notifications weren't sent. How do we handle partial failures?

**The Saga solution:** Track each step in a `PaymentSaga` table. If a step fails, run **compensating transactions** — actions that undo or mitigate the failed operation.

---

## Architecture

```
POST /api/payments/create-order
    → PaymentService.createOrder()
    → RazorpayClient.orders.create()
    → Save Payment (status=PENDING)
    → SagaOrchestrator.startSaga() → Save PaymentSaga (step=ORDER_CREATED)

POST /api/payments/verify
    → PaymentService.verifyPayment()
    → Verify Razorpay signature
    → Update Payment (status=AWAITING_APPROVAL)
    → SagaOrchestrator.onPaymentCaptured() → step=PAYMENT_CAPTURED
    → SagaOrchestrator.onAwaitingApproval() → step=AWAITING_APPROVAL, publish event

PUT /api/payments/{id}/accept
    → PaymentService.acceptPayment()
    → Update Payment (status=SUCCESS)
    → SagaOrchestrator.onPaymentAccepted()
        → step=ACCEPTED, status=COMPLETED
        → Publish payment.success event
        → Send success emails

PUT /api/payments/{id}/reject
    → PaymentService.rejectPayment()
    → Update Payment (status=REJECTED)
    → SagaOrchestrator.compensate()
        → Issue Razorpay refund
        → Publish payment.failed event
        → Send rejection email
        → step=COMPENSATED (or FAILED if refund failed)
```

---

## File 1: PaymentController.java

```java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            Map<String, Object> response = paymentService.createOrder(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{paymentId}/saga")
    public ResponseEntity<PaymentSaga> getSagaStatus(@PathVariable Long paymentId) {
        try {
            return ResponseEntity.ok(sagaOrchestrator.getSagaByPaymentId(paymentId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
```

The `/saga` endpoint is for **monitoring/debugging** — it shows the current state of the Saga for a payment. In production, this would be admin-only.

**`Map<String, Object>` as response type** — More flexible than a typed DTO here. The response includes Razorpay-specific fields (`orderId`, `keyId`) that are passed directly to the frontend for Razorpay's JS SDK.

---

## File 2: PaymentService.java

### createOrder:

```java
@Override
public Map<String, Object> createOrder(CreateOrderRequest request) throws RazorpayException {
    // Step 1: Create order in Razorpay
    int amountInPaise = (int) (request.getAmount() * 100);  // Razorpay uses paise (1 rupee = 100 paise)

    JSONObject orderRequest = new JSONObject();
    orderRequest.put("amount", amountInPaise);
    orderRequest.put("currency", "INR");
    orderRequest.put("receipt", "receipt_" + System.currentTimeMillis());

    Order razorpayOrder = razorpayClient.orders.create(orderRequest);

    // Step 2: Save payment record to DB
    Payment payment = new Payment();
    payment.setRazorpayOrderId(razorpayOrder.get("id"));
    payment.setStatus(Payment.PaymentStatus.PENDING);
    Payment saved = paymentRepository.save(payment);

    // Step 3: Start saga
    sagaOrchestrator.startSaga(saved);

    // Step 4: Return Razorpay response to frontend
    Map<String, Object> response = new HashMap<>();
    response.put("orderId", razorpayOrder.get("id"));
    response.put("amount", amountInPaise);
    response.put("currency", "INR");
    response.put("keyId", razorpayKeyId);  // Frontend needs this to initialize Razorpay SDK
    return response;
}
```

**`amountInPaise`** — Razorpay expects amounts in paise (smallest currency unit). ₹500 = 50000 paise.

**`razorpayClient.orders.create(orderRequest)`** — This is the Razorpay Java SDK call. It makes an API call to Razorpay's servers and returns an order ID. The frontend uses this order ID to open Razorpay's payment UI.

**`receipt`** — A unique identifier for the order, used for reconciliation. We use timestamp to make it unique.

### verifyPayment:

```java
@Override
public Map<String, Object> verifyPayment(VerifyPaymentRequest request) {
    // Step 1: Verify Razorpay signature
    boolean isValid;
    try {
        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", request.getRazorpayOrderId());
        attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
        attributes.put("razorpay_signature", request.getRazorpaySignature());
        isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
    } catch (Exception e) { ... }

    if (isValid) {
        payment.setStatus(Payment.PaymentStatus.AWAITING_APPROVAL);
        paymentRepository.save(payment);
        sagaOrchestrator.onPaymentCaptured(payment);
        sagaOrchestrator.onAwaitingApproval(payment);
        ...
    } else {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        sagaOrchestrator.onStepFailed(payment.getId(), "Razorpay signature invalid");
    }
}
```

**Signature Verification — Critical Security:**

After the user pays on Razorpay's UI:
1. Razorpay sends `razorpay_order_id`, `razorpay_payment_id`, and `razorpay_signature` to the frontend.
2. Frontend sends these to your backend.
3. Backend verifies: `HMAC_SHA256(razorpay_order_id + "|" + razorpay_payment_id, razorpay_key_secret) == razorpay_signature`

If an attacker intercepts the payment response and changes `razorpay_payment_id`, the signature won't match. This PREVENTS a malicious user from claiming payment success without actually paying.

**`Utils.verifyPaymentSignature(attributes, razorpayKeySecret)`** — Razorpay SDK method that performs the HMAC-SHA256 verification.

---

## File 3: SagaOrchestrator.java

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final PaymentSagaRepository sagaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final EmailService emailService;
    private final RazorpayClient razorpayClient;
```

**SagaStep enum:**
```java
public enum SagaStep {
    ORDER_CREATED,      // Razorpay order created
    PAYMENT_CAPTURED,   // Payment completed on Razorpay
    AWAITING_APPROVAL,  // Waiting for founder
    ACCEPTED,           // Founder approved → HAPPY PATH
    COMPENSATING,       // Running compensations
    COMPENSATED,        // Refund issued
    FAILED              // Compensation failed
}
```

**SagaStatus enum:**
```java
public enum SagaStatus {
    IN_PROGRESS,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
```

### compensate (most complex method):

```java
@Transactional
public boolean compensate(Payment payment) {
    PaymentSaga saga = sagaRepository.findByPaymentId(payment.getId()).orElse(null);

    if (saga != null) {
        saga.setCurrentStep(SagaStep.COMPENSATING);
        saga.setStatus(SagaStatus.COMPENSATING);
        sagaRepository.save(saga);
    }

    // Compensating Transaction 1: Refund via Razorpay
    boolean refundSucceeded = false;
    if (payment.getRazorpayPaymentId() != null) {
        try {
            int amountInPaise = (int) (payment.getAmount() * 100);
            JSONObject refundOptions = new JSONObject();
            refundOptions.put("amount", amountInPaise);
            refundOptions.put("speed", "normal");  // "optimum" = faster but costs more
            razorpayClient.payments.refund(payment.getRazorpayPaymentId(), refundOptions);
            refundSucceeded = true;
        } catch (Exception e) {
            if (saga != null) saga.setFailureReason("Razorpay refund failed: " + e.getMessage());
            log.error("[SAGA] Razorpay refund failed. reason={}", e.getMessage());
        }
    }

    // Compensating Transaction 2: Always notify (even if refund failed)
    publishEvent(payment, RabbitMQConfig.PAYMENT_FAILED_KEY, "REJECTED");

    // Compensating Transaction 3: Send rejection email
    sendEmailSafely(() -> emailService.sendPaymentRejectedEmailToInvestor(payment),
            "rejection email to investor", payment.getId());

    // Update saga final state
    if (saga != null) {
        boolean success = refundSucceeded || payment.getRazorpayPaymentId() == null;
        saga.setCurrentStep(success ? SagaStep.COMPENSATED : SagaStep.FAILED);
        saga.setStatus(success ? SagaStatus.COMPENSATED : SagaStatus.FAILED);
        sagaRepository.save(saga);
    }

    return refundSucceeded || payment.getRazorpayPaymentId() == null;
}
```

**Key design decisions in compensation:**

1. **Refund is attempted FIRST** — money is the most critical compensation.
2. **Notification is ALWAYS published** — even if refund fails. The user needs to know what happened.
3. **Email is sent with `sendEmailSafely`** — wrapped to never throw. Email failure is non-critical.
4. **Saga state tracks partial failures** — if refund fails, saga is marked `FAILED` for manual intervention. The failure reason is persisted (`saga.setFailureReason(...)`) for audit/debugging.

**`payment.getRazorpayPaymentId() == null`**
- Some payments might not have a Razorpay payment ID (e.g., order created but payment never completed).
- In this case, there's nothing to refund — compensation is considered "successful" without a refund.

---

## File 4: PaymentSaga.java (Entity)

```java
@Entity
@Table(name = "payment_sagas")
public class PaymentSaga {

    @Column(nullable = false, unique = true)
    private Long paymentId;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    @Enumerated(EnumType.STRING)
    private SagaStep currentStep;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @Column(length = 1000)
    private String failureReason;    // Stores failure details for debugging

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

**`@PrePersist` and `@PreUpdate`**
- `@PrePersist` — called before the entity is first inserted.
- `@PreUpdate` — called before any update to an existing entity.
- Together they maintain `createdAt` (set once) and `updatedAt` (updated on every save).

**`failureReason`** — If Razorpay refund fails, the error message is stored here. Operations team can see what went wrong and manually process the refund if needed.

---

## File 5: RabbitMQConfig.java

```java
@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "founderlink.exchange";
    public static final String PAYMENT_SUCCESS_KEY = "payment.success";
    public static final String PAYMENT_FAILED_KEY = "payment.failed";
    public static final String PAYMENT_PENDING_KEY = "payment.pending";

    @Bean
    public TopicExchange paymentExchange() { ... }

    @Bean
    public Queue paymentSuccessQueue() { return QueueBuilder.durable("payment.success.queue").build(); }

    @Bean
    public Queue paymentFailedQueue() { return QueueBuilder.durable("payment.failed.queue").build(); }

    @Bean
    public Queue paymentPendingQueue() { return QueueBuilder.durable("payment.pending.queue").build(); }

    @Bean
    public Binding paymentSuccessBinding(Queue paymentSuccessQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentSuccessQueue).to(paymentExchange).with(PAYMENT_SUCCESS_KEY);
    }
    // ... similar for failed and pending
}
```

PaymentService publishes 3 types of events:
- `payment.pending` → founder gets notified that payment is awaiting their approval
- `payment.success` → both parties notified of successful investment
- `payment.failed` → both parties notified, investor gets refund notification

---

## Complete Payment Flow End-to-End

```
1. Investor (id=5) wants to invest ₹5,00,000 in TechCorp (startupId=1, founderId=2)
   POST /api/payments/create-order
   {investorId: 5, founderId: 2, startupId: 1, amount: 500000, ...}
   ↓
   Razorpay order created → returns {orderId: "order_xyz", amount: 50000000, keyId: "rzp_test_..."}
   Payment saved: {status: PENDING, razorpayOrderId: "order_xyz"}
   Saga started: {step: ORDER_CREATED, status: IN_PROGRESS}

2. Investor pays on Razorpay UI (card/UPI)
   POST /api/payments/verify
   {razorpayOrderId: "order_xyz", razorpayPaymentId: "pay_abc", razorpaySignature: "..."}
   ↓
   Signature verified ✓
   Payment: {status: AWAITING_APPROVAL}
   Saga: {step: AWAITING_APPROVAL}
   Event published: payment.pending → NotificationService → Founder notified

3a. Founder accepts:
    PUT /api/payments/{id}/accept
    ↓
    Payment: {status: SUCCESS}
    Saga: {step: ACCEPTED, status: COMPLETED}
    Event: payment.success → Both notified
    Emails: success email to investor + received email to founder

3b. Founder rejects:
    PUT /api/payments/{id}/reject
    ↓
    Payment: {status: REJECTED}
    Saga: {step: COMPENSATING}
    Razorpay refund issued for ₹5,00,000
    Saga: {step: COMPENSATED}
    Event: payment.failed → Both notified
    Email: rejection email to investor
```

---

## Interview Q&A

**Q: What is the Saga pattern and why do we use it in PaymentService?**

The Saga pattern manages distributed transactions that span multiple services and cannot use a traditional ACID database transaction. In FounderLink's payment flow, a single business transaction involves: creating a Razorpay order, verifying payment, updating the database, publishing events to RabbitMQ, and sending emails — across different systems. We use an orchestration-style Saga: `SagaOrchestrator` drives the workflow step by step, recording each step in the `payment_sagas` table. If the founder rejects the payment, the orchestrator runs **compensating transactions** in reverse order to undo the forward steps: issue a Razorpay refund, publish failure events, send emails.

**Q: Why is Razorpay signature verification critical?**

Without signature verification, any malicious user could claim payment success by sending fake `razorpayOrderId` and `razorpayPaymentId` values. The Razorpay signature is an HMAC-SHA256 hash of `{orderId}|{paymentId}` signed with your private API secret key. Only Razorpay (who has your key) and your server (which has the key) can generate this signature. When your server receives the payment details, it re-computes the HMAC and compares. If they match, the payment genuinely came from Razorpay. If not, someone tampered with it. `Utils.verifyPaymentSignature()` from the Razorpay Java SDK performs this verification.

**Q: What happens if the Razorpay refund fails during compensation?**

The `compensate()` method wraps the refund call in a try-catch. If it fails, `refundSucceeded = false` and the saga is marked `FAILED` with the failure reason stored in the `failureReason` column. The notifications and emails are STILL sent (they don't depend on refund success). The `FAILED` saga state is an audit trail — operations team can query payment sagas with `status = 'FAILED'` and manually process refunds. This approach prioritizes notification delivery over blocking on refund failure.

**Q: What is `@PrePersist` vs `@PreUpdate`?**

`@PrePersist` is a JPA lifecycle callback that runs before an entity is first inserted into the database. `@PreUpdate` runs before any existing entity is updated. In `PaymentSaga`, `@PrePersist` sets both `createdAt` and `updatedAt` to now. `@PreUpdate` only sets `updatedAt` to now. Together, they maintain an accurate audit trail: `createdAt` never changes after insertion, `updatedAt` always reflects the most recent modification.

**Q: How does the payment service know the paise amount for refund?**

Razorpay uses paise (1 rupee = 100 paise). The `Payment` entity stores `amount` in rupees as a decimal. When creating the Razorpay order: `(int) (request.getAmount() * 100)`. The same conversion is done for refunds: `(int) (payment.getAmount() * 100)`. We use `(int)` cast which truncates — this is acceptable for rupee amounts (amounts should be whole rupees, not fractions of a paise).
