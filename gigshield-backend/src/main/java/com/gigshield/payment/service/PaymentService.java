// com/gigshield/payment/service/PaymentService.java
package com.gigshield.payment.service;

import com.gigshield.payment.dto.CreateOrderResponse;
import com.gigshield.payment.entity.PaymentRecord;
import com.gigshield.payment.enums.PaymentStatus;
import com.gigshield.payment.enums.PaymentType;
import com.gigshield.payment.repository.PaymentRepository;
import com.gigshield.policy.service.PolicyService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RazorpayClient    razorpayClient;
    private final PaymentRepository paymentRepository;
    private final PolicyService     policyService;

    @Value("${gigshield.razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${gigshield.razorpay.payout-account-id:}")
    private String payoutAccountId;

    // ── Premium collection ────────────────────────────────────────────────────

    @Transactional
    public CreateOrderResponse createPremiumOrder(Long userId, Long policyId, int amountInr) {
        try {
            JSONObject options = new JSONObject();
            options.put("amount",          amountInr * 100);  // paise
            options.put("currency",        "INR");
            options.put("receipt",         "policy_" + policyId);
            options.put("payment_capture", 1);

            Order order = razorpayClient.orders.create(options);
            String orderId = order.get("id");

            PaymentRecord record = PaymentRecord.builder()
                    .userId(userId)
                    .policyId(policyId)
                    .type(PaymentType.PREMIUM_COLLECTION)
                    .amountInr(amountInr)
                    .status(PaymentStatus.PENDING)
                    .razorpayOrderId(orderId)
                    .build();
            paymentRepository.save(record);

            log.info("Razorpay order {} created for policyId={} userId={}", orderId, policyId, userId);
            return new CreateOrderResponse(orderId, amountInr, "INR");

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage());
            throw new RuntimeException("Payment order creation failed", e);
        }
    }

    // ── Webhook handler ───────────────────────────────────────────────────────

    @Transactional
    public void handleWebhook(String payload, String signature) {
        verifyWebhookSignature(payload, signature);

        JSONObject json  = new JSONObject(payload);
        String     event = json.getString("event");

        if ("payment.captured".equals(event)) {
            JSONObject payment = json
                    .getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String orderId   = payment.getString("order_id");
            String paymentId = payment.getString("id");

            paymentRepository.findByRazorpayOrderId(orderId).ifPresent(record -> {
                record.setStatus(PaymentStatus.SUCCESS);
                record.setRazorpayPaymentId(paymentId);
                paymentRepository.save(record);

                if (record.getPolicyId() != null) {
                    policyService.activatePolicy(record.getPolicyId(), paymentId);
                }
                log.info("Payment captured: orderId={} paymentId={}", orderId, paymentId);
            });
        }

        if ("payment.failed".equals(event)) {
            JSONObject payment = json
                    .getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");
            String orderId = payment.getString("order_id");
            paymentRepository.findByRazorpayOrderId(orderId).ifPresent(record -> {
                record.setStatus(PaymentStatus.FAILED);
                record.setFailureReason(payment.optString("error_description", "unknown"));
                paymentRepository.save(record);
            });
        }
    }

    // ── Claim payout ──────────────────────────────────────────────────────────

    @Transactional
    public void initiateClaimPayout(String claimId, Long userId, int amountInr) {
        // Idempotency: skip if payout already recorded
        if (paymentRepository.findByClaimId(claimId).isPresent()) {
            log.warn("Payout already recorded for claimId={}", claimId);
            return;
        }

        PaymentRecord record = PaymentRecord.builder()
                .userId(userId)
                .claimId(claimId)
                .type(PaymentType.CLAIM_PAYOUT)
                .amountInr(amountInr)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(record);

        // TODO: POST to Razorpay /v1/payouts when fund_account_id is registered
        // In test mode: log only
        log.info("[PAYOUT] ₹{} queued for claimId={} userId={}", amountInr, claimId, userId);

        record.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.save(record);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void verifyWebhookSignature(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook secret not configured — skipping signature verification");
            return;
        }
        try {
            boolean valid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!valid) throw new SecurityException("Invalid Razorpay webhook signature");
        } catch (RazorpayException e) {
            throw new SecurityException("Webhook signature verification failed: " + e.getMessage());
        }
    }
}