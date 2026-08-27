// com/gigshield/user/entity/User.java
package com.gigshield.user.entity;

import com.gigshield.user.enums.DeliveryPlatform;
import com.gigshield.user.enums.UserRole;
import com.gigshield.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users",
        indexes = {
                @Index(name = "idx_user_phone", columnList = "phone"),
                @Index(name = "idx_user_city",  columnList = "city")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    // passwordHash removed — OTP is the only credential

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private int fraudStrikeCount;

    @Column(nullable = false)
    private Double weeklyIncomeEstimate;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt        = LocalDateTime.now();
        updatedAt        = LocalDateTime.now();
        fraudStrikeCount = 0;
        status           = UserStatus.ACTIVE;
        role             = UserRole.ROLE_WORKER;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }

    // ── UserDetails — OTP auth, no password needed ────────────────────────────
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    // No password — return null, Spring Security never checks it
    // because we bypass DaoAuthenticationProvider entirely
    @Override public String  getPassword()             { return null; }
    @Override public String  getUsername()             { return phone; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return status != UserStatus.BANNED; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return status == UserStatus.ACTIVE; }
}