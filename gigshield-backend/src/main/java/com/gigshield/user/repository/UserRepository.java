// com/gigshield/user/repository/UserRepository.java
package com.gigshield.user.repository;

import com.gigshield.user.entity.User;
import com.gigshield.user.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    List<User> findByCityAndStatus(String city, UserStatus status);

    @Modifying
    @Query("UPDATE User u SET u.fraudStrikeCount = u.fraudStrikeCount + 1 WHERE u.id = :id")
    void incrementFraudStrike(Long id);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    void updateStatus(Long id, UserStatus status);
}