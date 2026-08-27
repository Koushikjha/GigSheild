// com/gigshield/config/AppConstants.java
package com.gigshield.config;

public final class  AppConstants {

    private AppConstants() {}

    // ── Payout Caps (₹) ───────────────────────────────────────────────────────
    public static final int    WAR_PAYOUT_CAP_INR            = 500;
    public static final int    TRAFFIC_PAYOUT_CAP_INR        = 200;
    /** A single missed order, not a week of disruption — capped well below the weekly max payout. */
    public static final int    ORDER_CANCELLED_PAYOUT_CAP_INR = 150;

    // ── Self-reported cancelled-order claims ────────────────────────────────────
    /**
     * A worker must report a cancelled order within this many hours of it
     * happening. Bounds fraud exposure (a claim from weeks ago is much
     * harder to honestly corroborate) and comfortably fits inside the ML
     * sidecar's ~90-day historical-weather window, so time is never the
     * binding constraint on genuineness checking — this is.
     */
    public static final long   ORDER_CANCELLED_REPORT_WINDOW_HOURS = 72;
    /** Small clock-skew allowance so "just now" from a slightly-fast client clock isn't rejected as "future". */
    public static final long   ORDER_CANCELLED_FUTURE_TOLERANCE_MINUTES = 5;

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

    // ── Risk-based pricing ────────────────────────────────────────────────────
    /**
     * Each tier's flat premium (PREMIUM_STANDARD_INR etc.) is the base price
     * for a place with "average" risk. PolicyService scales that base by a
     * multiplier derived linearly from the place's live ML risk-score
     * (0-100, from RiskService/MlServiceClient): a 0-risk city pays the
     * floor multiplier, a 100-risk city pays the ceiling multiplier, and
     * everything between is interpolated. This is what actually prices
     * policies by the risk score of the place — the flat per-tier numbers
     * alone no longer are the price, just the anchor.
     */
    public static final double RISK_PREMIUM_MULTIPLIER_FLOOR = 0.80;
    public static final double RISK_PREMIUM_MULTIPLIER_CEIL  = 1.35;

    // ── Kept for backwards compatibility ──────────────────────────────────────
    public static final double PAYOUT_INCOME_RATIO_MIN       = 0.30;
    public static final double PAYOUT_INCOME_RATIO_MAX       = 0.40;

    // ── Fraud ─────────────────────────────────────────────────────────────────
    public static final int    FRAUD_AUTO_APPROVE_THRESHOLD  = 40;
    public static final int    FRAUD_STRIKE_BAN_COUNT        = 3;

    // ── Event genuineness gate (Kafka claims-automation → ML corroboration) ────
    /**
     * Minimum independent ML risk-score (0-100) a city must show for a
     * disruption event over it to be treated as genuine. Below this, ML's
     * own risk model disagrees that the city looks disrupted right now, so
     * DisruptionEventVerificationService holds automation rather than fan
     * claims out on an uncorroborated signal.
     */
    public static final double EVENT_GENUINE_MIN_RISK_SCORE  = 20.0;

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