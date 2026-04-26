// com/gigshield/auth/sms/SmsClient.java
package com.gigshield.auth.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsClient {

    @Value("${gigshield.sms.mock:true}")
    private boolean mockMode;

    @Value("${gigshield.sms.provider-url:}")
    private String providerUrl;

    @Value("${gigshield.sms.api-key:}")
    private String apiKey;

    /**
     * Sends OTP SMS to the given phone number.
     * In dev/mock mode: logs the OTP instead of sending.
     * In prod: integrate your SMS provider here (Twilio, MSG91, Fast2SMS etc).
     */
    public void sendOtp(String phone, String otp) {
        if (mockMode) {
            // In dev the OTP is visible in logs
            log.info("╔══════════════════════════════╗");
            log.info("║  [MOCK SMS] To: +91{}  ║", phone);
            log.info("║  OTP: {}                    ║", otp);
            log.info("╚══════════════════════════════╝");
            return;
        }

        // Production: wire your SMS provider SDK here
        // Example for MSG91:
        // msg91Client.send(phone, "Your GigShield OTP is: " + otp);

        // Example for Fast2SMS:
        // fast2SmsClient.send(phone, otp);

        // Throw if provider not configured in prod
        if (providerUrl.isBlank() || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "SMS provider not configured. " +
                            "Set gigshield.sms.provider-url and gigshield.sms.api-key");
        }

        log.info("OTP sent to phone={}", phone);
    }
}