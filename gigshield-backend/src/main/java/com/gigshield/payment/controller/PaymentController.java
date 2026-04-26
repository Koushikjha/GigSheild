// com/gigshield/payment/controller/PaymentController.java
package com.gigshield.payment.controller;

import com.gigshield.payment.dto.CreateOrderResponse;
import com.gigshield.payment.service.PaymentService;
import com.gigshield.policy.service.PolicyService;
import com.gigshield.user.entity.User;
import com.gigshield.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PolicyService  policyService;
    private final UserService    userService;

    /**
     * Worker calls this after GET /policies to get a Razorpay order to pay.
     */
    @PostMapping("/order/{policyId}")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long policyId) {

        User user   = userService.findByPhone(userDetails.getUsername());
        var  policy = policyService.getActivePolicyEntity(user.getId());

        if (!policy.getId().equals(policyId)) {
            return ResponseEntity.badRequest().build();
        }

        CreateOrderResponse order = paymentService
                .createPremiumOrder(user.getId(), policyId, policy.getPremiumPaid());
        return ResponseEntity.ok(order);
    }

    /**
     * Razorpay posts signed events here.
     * Permitted without JWT — verified by webhook signature inside service.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}