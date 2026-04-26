// com/gigshield/claim/controller/ClaimController.java
package com.gigshield.claim.controller;

import com.gigshield.claim.dto.AdminReviewRequest;
import com.gigshield.claim.dto.ClaimResponse;
import com.gigshield.claim.service.ClaimService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;

    @GetMapping
    public ResponseEntity<Page<ClaimResponse>> myClaims(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(claimService.getClaimsForUser(user.getUsername(), page));
    }

    @GetMapping("/flagged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClaimResponse>> flaggedClaims() {
        return ResponseEntity.ok(claimService.getFlaggedClaims());
    }

    @PostMapping("/{claimId}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponse> review(
            @PathVariable String claimId,
            @Valid @RequestBody AdminReviewRequest request) {
        return ResponseEntity.ok(claimService.adminReview(claimId, request));
    }
}