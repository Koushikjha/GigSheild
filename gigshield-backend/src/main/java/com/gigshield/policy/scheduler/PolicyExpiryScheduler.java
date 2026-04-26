// com/gigshield/policy/scheduler/PolicyExpiryScheduler.java
package com.gigshield.policy.scheduler;

import com.gigshield.policy.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyExpiryScheduler {

    private final PolicyService policyService;

    /** Runs daily at midnight IST */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void expireStalePolices() {
        log.info("Running policy expiry job...");
        policyService.expireStalePolices();
    }
}