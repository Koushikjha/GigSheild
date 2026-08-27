// com/gigshield/risk/repository/RiskRepository.java
package com.gigshield.risk.repository;

import com.gigshield.risk.entity.RiskProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RiskRepository extends JpaRepository<RiskProfile, Long> {

    Optional<RiskProfile> findByUserId(Long userId);

    Optional<RiskProfile> findByCityAndRiskBand(String city, String riskBand);
}