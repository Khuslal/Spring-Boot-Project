package com.mobile_shop.mobile_shop.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class EsewaSignatureUtil {

    private static final String SIGNATURE_ALGORITHM = "HmacSHA256";

    // Secret key for UAT (Testing)
    public static final String UAT_SECRET_KEY = "8gBm/:&EnhH.1/q";

    // Merchant code for UAT (Testing)
    public static final String UAT_MERCHANT_CODE = "EPAYTEST";

    /**
     * Generate eSewa signature
     * 
     * @param secretKey The merchant secret key
     * @param message   The message to sign (format:
     *                  total_amount=100,transaction_uuid=123,product_code=EPAYTEST)
     * @return Base64 encoded signature
     */
    public static String generateSignature(String secretKey, String message) {
        try {
            Mac mac = Mac.getInstance(SIGNATURE_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secretKey.getBytes(StandardCharsets.UTF_8),
                    SIGNATURE_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate eSewa signature: " + e.getMessage(), e);
        }
    }

    /**
     * Build signature message for eSewa payment request
     * Format: total_amount=100,transaction_uuid=123,product_code=EPAYTEST
     * 
     * @param totalAmount     The total amount
     * @param transactionUuid The transaction UUID
     * @param productCode     The merchant product code
     * @return Formatted signature message
     */
    public static String buildSignatureMessage(Double totalAmount, String transactionUuid, String productCode) {
        return String.format("total_amount=%.2f,transaction_uuid=%s,product_code=%s",
                totalAmount, transactionUuid, productCode);
    }

    /**
     * Verify eSewa signature
     * 
     * @param secretKey         The merchant secret key
     * @param message           The original message
     * @param receivedSignature The signature received from eSewa
     * @return true if signature matches
     */
    public static boolean verifySignature(String secretKey, String message, String receivedSignature) {
        try {
            String expectedSignature = generateSignature(secretKey, message);
            return expectedSignature.equals(receivedSignature);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate signature for UAT environment
     */
    public static String generateSignatureForUAT(Double totalAmount, String transactionUuid) {
        String message = buildSignatureMessage(totalAmount, transactionUuid, UAT_MERCHANT_CODE);
        return generateSignature(UAT_SECRET_KEY, message);
    }

    /**
     * Verify signature for UAT environment
     */
    public static boolean verifySignatureForUAT(Double totalAmount, String transactionUuid, String receivedSignature) {
        String message = buildSignatureMessage(totalAmount, transactionUuid, UAT_MERCHANT_CODE);
        return verifySignature(UAT_SECRET_KEY, message, receivedSignature);
    }
}