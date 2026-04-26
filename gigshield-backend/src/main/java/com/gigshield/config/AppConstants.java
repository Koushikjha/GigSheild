// com/gigshield/config/AppConstants.java
package com.gigshield.config;

public final class AppConstants {

    private AppConstants() {}

    // ── Payout Caps (₹) ───────────────────────────────────────────────────────
    public static final int    WAR_PAYOUT_CAP_INR            = 500;
    public static final int    TRAFFIC_PAYOUT_CAP_INR        = 200;

    // ── Tier Premiums (₹/week) ────────────────────────────────────────────────
    public static final int    PREMIUM_STANDARD_INR          = 15;
    public static final int    PREMIUM_GOLD_INR              = 22;
    public static final int    PREMIUM_PREMIUM_INR           = 30;

    // ── Kept for ML clamping bounds ───────────────────────────────────────────
    public static final int    PREMIUM_MIN_INR               = 15;
    public static final int    PREMIUM_MAX_INR               = 30;

    // ── Tier Payout Ratios ────────────────────────────────────────────────────
    public static final double PAYOUT_RATIO_STANDARD         = 0.30;
    public static final double PAYOUT_RATIO_GOLD             = 0.35;
    public static final double PAYOUT_RATIO_PREMIUM          = 0.40;

    // ── Kept for backwards compatibility ──────────────────────────────────────
    public static final double PAYOUT_INCOME_RATIO_MIN       = 0.30;
    public static final double PAYOUT_INCOME_RATIO_MAX       = 0.40;

    // ── Fraud ─────────────────────────────────────────────────────────────────
    public static final int    FRAUD_AUTO_APPROVE_THRESHOLD  = 40;
    public static final int    FRAUD_STRIKE_BAN_COUNT        = 3;

    // ── JWT ───────────────────────────────────────────────────────────────────
    public static final long   JWT_EXPIRY_MS                 = 86_400_000L;
    public static final String JWT_HEADER                    = "Authorization";
    public static final String JWT_PREFIX                    = "Bearer ";

    // ── Redis TTLs (seconds) ─────────────────────────────────────────────────
    public static final long   SESSION_TTL_SEC               = 86_400L;
    public static final long   RISK_SCORE_CACHE_TTL_SEC      = 3_600L;
    public static final long   TRIGGER_CACHE_TTL_SEC         = 900L;

    // ── ML Sidecar ────────────────────────────────────────────────────────────
    public static final String ML_RISK_SCORE_PATH            = "/risk-score";
    public static final String ML_FRAUD_CHECK_PATH           = "/fraud-check";
    public static final String ML_TRIGGER_CHECK_PATH         = "/trigger-check";

    // ── Pagination ────────────────────────────────────────────────────────────
    public static final int    DEFAULT_PAGE_SIZE             = 20;

    // ── Claim / Policy ────────────────────────────────────────────────────────
    public static final int    POLICY_DURATION_DAYS          = 7;
    public static final double DEFAULT_WEEKLY_INCOME_INR     = 3_500.0;
}