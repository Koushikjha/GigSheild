// com/gigshield/payment/dto/CreateOrderResponse.java
package com.gigshield.payment.dto;

import lombok.*;

@Data @AllArgsConstructor
public class CreateOrderResponse {
    private String  razorpayOrderId;
    private Integer amountInr;
    private String  currency;
}