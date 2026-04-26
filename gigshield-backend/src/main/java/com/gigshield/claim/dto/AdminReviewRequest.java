// com/gigshield/claim/dto/AdminReviewRequest.java
package com.gigshield.claim.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminReviewRequest {
    @NotNull
    private Boolean approve;

    private String adminNote;
}