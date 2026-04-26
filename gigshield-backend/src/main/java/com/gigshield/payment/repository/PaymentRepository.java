// com/gigshield/payment/repository/PaymentRepository.java
package com.gigshield.payment.repository;

import com.gigshield.payment.entity.PaymentRecord;
import com.gigshield.payment.enums.PaymentStatus;
import com.gigshield.payment.enums.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {

    List<PaymentRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PaymentRecord> findByRazorpayOrderId(String orderId);

    Optional<PaymentRecord> findByClaimId(String claimId);

    List<PaymentRecord> findByTypeAndStatus(PaymentType type, PaymentStatus status);
}