package com.capgemini.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    private Long investorId;
    private Long founderId;
    private Long startupId;
    private String startupName;
    private String investorName;
    private String investorEmail;
    private String founderEmail;

    private Double amount;
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING,
        AWAITING_APPROVAL,
        SUCCESS,
        FAILED,
        REJECTED
    }
}
