// com/gigshield/fraud/repository/FraudRepository.java
package com.gigshield.fraud.repository;

import com.gigshield.fraud.entity.FraudRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FraudRepository extends JpaRepository<FraudRecord, Long> {

    List<FraudRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT COUNT(f) FROM FraudRecord f WHERE f.userId = :userId AND f.strikeIssued = true")
    int countStrikesByUserId(Long userId);

    boolean existsByClaimId(String claimId);
}