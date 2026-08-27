// com/gigshield/policy/controller/PolicyController.java
package com.gigshield.policy.controller;

import com.gigshield.policy.dto.PolicyResponse;
import com.gigshield.policy.dto.PolicyTierInfoResponse;
import com.gigshield.policy.dto.PurchasePolicyRequest;
import com.gigshield.policy.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    /**
     * Returns all 3 tiers with premiums and estimated payouts
     * based on the authenticated user's income profile.
     * Frontend calls this to render the plan selection screen.
     */
    @GetMapping("/tiers")
    public ResponseEntity<List<PolicyTierInfoResponse>> getTiers(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                policyService.getAllTierInfo(user.getUsername()));
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> purchase(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PurchasePolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.purchasePolicy(
                        user.getUsername(), request));
    }

    @GetMapping("/active")
    public ResponseEntity<PolicyResponse> getActive(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                policyService.getActivePolicy(user.getUsername()));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<PolicyResponse>> getHistory(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(
                policyService.getPolicyHistory(user.getUsername(), page));
    }
}