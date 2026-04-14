package com.capgemini.payment.service;

import com.capgemini.payment.entity.Payment;
import java.util.List;

// Optimized read layer for payments. Pulls directly from Redis cache whenever possible.
public interface PaymentQueryService {
    List<Payment> getPaymentsByInvestor(Long investorId);
    List<Payment> getPaymentsByFounder(Long founderId);
    List<Payment> getPaymentsByStartup(Long startupId);
}
