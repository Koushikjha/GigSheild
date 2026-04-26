// com/gigshield/claim/repository/ClaimRepository.java
package com.gigshield.claim.repository;

import com.gigshield.claim.document.Claim;
import com.gigshield.claim.enums.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClaimRepository extends MongoRepository<Claim, String> {

    Page<Claim> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Claim> findByStatus(ClaimStatus status);

    boolean existsByUserIdAndPolicyIdAndTriggerEvent(
            Long userId, Long policyId, com.gigshield.event.enums.EventType event);
}