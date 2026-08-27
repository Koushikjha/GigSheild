// com/gigshield/risk/controller/RiskController.java
package com.gigshield.risk.controller;

import com.gigshield.risk.dto.RiskScoreResponse;
import com.gigshield.risk.service.RiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService riskService;

    @GetMapping("/score")
    public ResponseEntity<RiskScoreResponse> getMyRiskScore(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(riskService.getRiskScoreForUser(user.getUsername()));
    }
}