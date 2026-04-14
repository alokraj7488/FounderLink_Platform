package com.capgemini.payment.controller;

import com.capgemini.payment.dto.CreateOrderRequest;
import com.capgemini.payment.dto.VerifyPaymentRequest;
import com.capgemini.payment.entity.Payment;
import com.capgemini.payment.saga.PaymentSaga;
import com.capgemini.payment.saga.SagaOrchestrator;
import com.capgemini.payment.service.PaymentCommandService;
import com.capgemini.payment.service.PaymentQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@Slf4j
public class PaymentController {

    private final PaymentCommandService paymentCommandService;
    private final PaymentQueryService paymentQueryService;
    private final SagaOrchestrator sagaOrchestrator;

    public PaymentController(PaymentCommandService paymentCommandService, PaymentQueryService paymentQueryService, SagaOrchestrator sagaOrchestrator) {
        this.paymentCommandService = paymentCommandService;
        this.paymentQueryService = paymentQueryService;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            Map<String, Object> response = paymentCommandService.createOrder(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating Razorpay order: ", e);
            String errorMessage = (e.getMessage() != null && !e.getMessage().isEmpty()) 
                    ? e.getMessage() 
                    : "Internal Server Error during order creation";
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@RequestBody VerifyPaymentRequest request) {
        Map<String, Object> response = paymentCommandService.verifyPayment(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{paymentId}/accept")
    public ResponseEntity<Map<String, Object>> acceptPayment(@PathVariable Long paymentId) {
        try {
            return ResponseEntity.ok(paymentCommandService.acceptPayment(paymentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{paymentId}/reject")
    public ResponseEntity<Map<String, Object>> rejectPayment(@PathVariable Long paymentId) {
        try {
            return ResponseEntity.ok(paymentCommandService.rejectPayment(paymentId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/investor/{investorId}")
    public ResponseEntity<List<Payment>> getPaymentsByInvestor(@PathVariable Long investorId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentsByInvestor(investorId));
    }

    @GetMapping("/founder/{founderId}")
    public ResponseEntity<List<Payment>> getPaymentsByFounder(@PathVariable Long founderId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentsByFounder(founderId));
    }

    @GetMapping("/startup/{startupId}")
    public ResponseEntity<List<Payment>> getPaymentsByStartup(@PathVariable Long startupId) {
        return ResponseEntity.ok(paymentQueryService.getPaymentsByStartup(startupId));
    }

    // Returns current saga state for a payment — useful for debugging/monitoring
    @GetMapping("/{paymentId}/saga")
    public ResponseEntity<PaymentSaga> getSagaStatus(@PathVariable Long paymentId) {
        try {
            return ResponseEntity.ok(sagaOrchestrator.getSagaByPaymentId(paymentId));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
