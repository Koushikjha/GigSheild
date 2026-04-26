// com/gigshield/policy/repository/PolicyRepository.java
package com.gigshield.policy.repository;

import com.gigshield.policy.entity.Policy;
import com.gigshield.policy.enums.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long> {

    Page<Policy> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<Policy> findByUserIdAndStatus(Long userId, PolicyStatus status);

    @Query("SELECT p FROM Policy p WHERE p.userId = :userId AND p.status = 'ACTIVE' " +
            "AND p.startDate <= :today AND p.endDate >= :today")
    Optional<Policy> findActivePolicy(Long userId, LocalDate today);

    @Query("SELECT p FROM Policy p WHERE p.status = 'ACTIVE' AND p.endDate < :today")
    List<Policy> findExpiredPolicies(LocalDate today);
}