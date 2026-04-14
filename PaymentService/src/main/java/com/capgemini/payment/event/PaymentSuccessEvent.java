package com.capgemini.payment.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Standardized payment event for distributed notification
public class PaymentSuccessEvent {
    private Long paymentId;
    private Long investorId;
    private Long founderId;
    private Long startupId;
    private String startupName;
    private String investorName;
    private Double amount;
    private String razorpayPaymentId;
    private String status;
}
