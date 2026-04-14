package com.capgemini.payment.service;

import com.capgemini.payment.dto.CreateOrderRequest;
import com.capgemini.payment.dto.VerifyPaymentRequest;
import com.capgemini.payment.entity.Payment;
import com.capgemini.payment.repository.PaymentRepository;
import com.capgemini.payment.saga.SagaOrchestrator;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PaymentService implements PaymentCommandService, PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final RazorpayClient razorpayClient;
    private final SagaOrchestrator sagaOrchestrator;
    private final String razorpayKeyId;
    private final String razorpayKeySecret;

    public PaymentService(PaymentRepository paymentRepository,
                          RazorpayClient razorpayClient,
                          SagaOrchestrator sagaOrchestrator,
                          @Value("${razorpay.key-id}") String razorpayKeyId,
                          @Value("${razorpay.key-secret}") String razorpayKeySecret) {
        this.paymentRepository = paymentRepository;
        this.razorpayClient = razorpayClient;
        this.sagaOrchestrator = sagaOrchestrator;
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
    }

    // â”€â”€ Step 1: Create Razorpay order â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @CacheEvict(value = {"investorPayments", "founderPayments", "startupPayments"}, allEntries = true)
    public Map<String, Object> createOrder(CreateOrderRequest request) throws RazorpayException {
        int amountInPaise = (int) (request.getAmount() * 100);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "receipt_" + System.currentTimeMillis());

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);

        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("id"));
        payment.setInvestorId(request.getInvestorId());
        payment.setFounderId(request.getFounderId());
        payment.setStartupId(request.getStartupId());
        payment.setStartupName(request.getStartupName());
        payment.setInvestorName(request.getInvestorName());
        payment.setInvestorEmail(request.getInvestorEmail());
        payment.setFounderEmail(request.getFounderEmail());
        payment.setAmount(request.getAmount());
        payment.setStatus(Payment.PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);

        // Saga Step 1: record saga start
        sagaOrchestrator.startSaga(saved);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", razorpayOrder.get("id"));
        response.put("amount", amountInPaise);
        response.put("currency", "INR");
        response.put("keyId", razorpayKeyId);
        return response;
    }

    // â”€â”€ Step 2: Verify Razorpay signature and capture payment â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @CacheEvict(value = {"investorPayments", "founderPayments", "startupPayments"}, allEntries = true)
    public Map<String, Object> verifyPayment(VerifyPaymentRequest request) {
        Map<String, Object> response = new HashMap<>();

        // Verify Razorpay signature
        boolean isValid;
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", request.getRazorpayOrderId());
            attributes.put("razorpay_payment_id", request.getRazorpayPaymentId());
            attributes.put("razorpay_signature", request.getRazorpaySignature());
            isValid = Utils.verifyPaymentSignature(attributes, razorpayKeySecret);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Signature verification error: " + e.getMessage());
            return response;
        }

        // Find payment record
        Payment payment;
        try {
            payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                    .orElseThrow(() -> new RuntimeException(
                            "Payment record not found for order: " + request.getRazorpayOrderId()));
        } catch (Exception e) {
            log.error("Payment lookup error: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return response;
        }

        // Preventing double capture if the verification is re-sent
        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            log.info("Payment already processed for order: {}. Current status: {}", 
                    request.getRazorpayOrderId(), payment.getStatus());
            response.put("success", true);
            response.put("message", "Payment already processed");
            response.put("paymentId", payment.getId());
            response.put("status", payment.getStatus().toString());
            return response;
        }

        if (isValid) {
            payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
            payment.setRazorpaySignature(request.getRazorpaySignature());
            payment.setStatus(Payment.PaymentStatus.AWAITING_APPROVAL);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Saga Step 2: payment captured
            sagaOrchestrator.onPaymentCaptured(payment);

            // Saga Step 3: notify founder and await approval
            sagaOrchestrator.onAwaitingApproval(payment);

            log.info("Payment Razorpay-verified, awaiting founder approval. paymentId={}", payment.getId());
            response.put("success", true);
            response.put("message", "Payment received. Awaiting founder approval.");
            response.put("paymentId", payment.getId());
            response.put("status", "AWAITING_APPROVAL");
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);

            // Record saga failure
            sagaOrchestrator.onStepFailed(payment.getId(), "Razorpay signature invalid");

            log.warn("Payment signature invalid for orderId: {}", request.getRazorpayOrderId());
            response.put("success", false);
            response.put("message", "Payment signature invalid");
        }
        return response;
    }

    // â”€â”€ Step 4a: Founder accepts â€” saga completes normally â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    @CacheEvict(value = {"investorPayments", "founderPayments", "startupPayments"}, allEntries = true)
    public Map<String, Object> acceptPayment(Long paymentId) {
        Map<String, Object> response = new HashMap<>();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Saga Step 4a: complete saga (publishes events + sends emails internally)
        sagaOrchestrator.onPaymentAccepted(payment);

        log.info("Founder accepted payment. paymentId={}", paymentId);
        response.put("success", true);
        response.put("message", "Investment accepted successfully");
        return response;
    }

    // â”€â”€ Step 4b: Founder rejects â€” saga runs compensating transactions â”€â”€â”€â”€â”€â”€â”€

    @Override
    @CacheEvict(value = {"investorPayments", "founderPayments", "startupPayments"}, allEntries = true)
    public Map<String, Object> rejectPayment(Long paymentId) {
        Map<String, Object> response = new HashMap<>();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.setStatus(Payment.PaymentStatus.REJECTED);
        payment.setUpdatedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        // Saga compensation: refund investor + notify + email
        boolean compensated;
        try {
            compensated = sagaOrchestrator.compensate(payment);
        } catch (Exception e) {
            log.error("Compensation threw unexpectedly for paymentId={}: {}", paymentId, e.getMessage());
            compensated = false;
        }

        if (compensated) {
            response.put("success", true);
            response.put("message", "Investment rejected and refund initiated to investor's account");
        } else {
            response.put("success", false);
            response.put("message", "Investment rejected but refund failed â€” please contact support");
        }
        return response;
    }

    // â”€â”€ Query methods â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    // Lightning validation fetch for investor portfolio directly off Redis.
    @Cacheable(value = "investorPayments", key = "#investorId")
    public List<Payment> getPaymentsByInvestor(Long investorId) {
        return paymentRepository.findByInvestorIdOrderByCreatedAtDesc(investorId);
    }

    @Override
    // Fast queries to view all incoming startup traction without hitting Postgres.
    @Cacheable(value = "founderPayments", key = "#founderId")
    public List<Payment> getPaymentsByFounder(Long founderId) {
        return paymentRepository.findByFounderIdOrderByCreatedAtDesc(founderId);
    }

    @Override
    @Cacheable(value = "startupPayments", key = "#startupId")
    public List<Payment> getPaymentsByStartup(Long startupId) {
        return paymentRepository.findByStartupIdOrderByCreatedAtDesc(startupId);
    }
}
