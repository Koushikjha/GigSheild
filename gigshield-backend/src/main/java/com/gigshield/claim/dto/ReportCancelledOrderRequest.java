// com/gigshield/claim/dto/ReportCancelledOrderRequest.java
package com.gigshield.claim.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Body for {@code POST /api/v1/claims/cancelled-order} — the only way an
 * ORDER_CANCELLED claim gets created (see EventType#ORDER_CANCELLED javadoc
 * for why this is worker-reported rather than automated).
 */
@Data
public class ReportCancelledOrderRequest {

    /**
     * When the order was actually cancelled — required, and load-bearing:
     * ClaimService#reportCancelledOrder corroborates genuineness against
     * conditions AS OF this moment, not whenever the claim happens to be
     * submitted. Must be within the last
     * AppConstants#ORDER_CANCELLED_REPORT_WINDOW_HOURS and not in the future.
     */
    @NotNull(message = "cancelledAt is required — when did the order get cancelled?")
    private LocalDateTime cancelledAt;

    @Size(max = 280, message = "note must be 280 characters or fewer")
    private String note;
}
