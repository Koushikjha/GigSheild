// com/gigshield/payment/dto/WebhookPayload.java
package com.gigshield.payment.dto;

import lombok.Data;

@Data
public class WebhookPayload {
    private String event;
    private Object payload;
}