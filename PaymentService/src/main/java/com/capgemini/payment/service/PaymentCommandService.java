package com.capgemini.payment.service;

import com.capgemini.payment.dto.CreateOrderRequest;
import com.capgemini.payment.dto.VerifyPaymentRequest;
import com.razorpay.RazorpayException;
import java.util.Map;

// Manages all payment writes and state changes. Evicts caching blocks upon updates.
public interface PaymentCommandService {
    Map<String, Object> createOrder(CreateOrderRequest request) throws RazorpayException;
    Map<String, Object> verifyPayment(VerifyPaymentRequest request);
    Map<String, Object> acceptPayment(Long paymentId);
    Map<String, Object> rejectPayment(Long paymentId);
}
