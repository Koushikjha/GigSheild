// com/gigshield/claim/repository/ClaimRepository.java
package com.gigshield.claim.repository;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.enums.ClaimStatus;
import com.gigshield.event.enums.EventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ClaimRepository extends MongoRepository<Claim, String> {

    Page<Claim> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Claim> findByStatus(ClaimStatus status);

    boolean existsByUserIdAndPolicyIdAndTriggerEvent(
            Long userId, Long policyId, EventType event);

    /**
     * Dedup for {@code ClaimService#reportCancelledOrder}: blocks an
     * accidental duplicate submission of the *same* cancellation (identical
     * reported time) while still allowing a worker to report multiple,
     * genuinely different cancelled orders across the week.
     */
    boolean existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(
            Long userId, Long policyId, EventType event, LocalDateTime eventOccurredAt);
}