package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.repository.OrderRepository;
import com.mobile_shop.mobile_shop.util.EsewaSignatureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    @Autowired
    private OrderRepository orderRepository;

    @Value("${esewa.merchant.code:EPAYTEST}")
    private String merchantCode;

    @Value("${esewa.merchant.secret:8gBm/:&EnhH.1/q}")
    private String merchantSecret;

    @Value("${esewa.base.url:https://rc-epay.esewa.com.np/api/epay/main/v2/form}")
    private String baseUrl;

    @Value("${esewa.status.url:https://rc.esewa.com.np/api/epay/transaction/status}")
    private String statusUrl;

    @Value("${esewa.success.url:http://localhost:8080/payment/success}")
    private String successUrl;

    @Value("${esewa.failure.url:http://localhost:8080/payment/failure}")
    private String failureUrl;

    /**
     * Generate signature for eSewa payment
     * 
     * @param orderId     The order ID
     * @param totalAmount The total amount to pay
     * @return HMAC-SHA256 signature
     */
    public String generateSignature(Double totalAmount, String transactionUuid) {
        String signatureMessage = EsewaSignatureUtil.buildSignatureMessage(
                totalAmount,
                transactionUuid,
                merchantCode);
        return EsewaSignatureUtil.generateSignature(merchantSecret, signatureMessage);
    }

    /**
     * Check transaction status from eSewa
     * 
     * @param transactionUuid The transaction UUID (order ID)
     * @param totalAmount     The total amount
     * @return Map containing status response
     */
    public Map<String, Object> checkTransactionStatus(String transactionUuid, Double totalAmount) {
        try {
            String url = statusUrl +
                    "?product_code=" + merchantCode +
                    "&total_amount=" + String.format("%.2f", totalAmount) +
                    "&transaction_uuid=" + transactionUuid;

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            // Log error
            System.err.println("Error checking transaction status: " + e.getMessage());
        }

        return new HashMap<>();
    }

    /**
     * Process payment success callback by payment reference ID
     */
    public String processSuccessByPaymentRefId(String paymentRefId, String totalAmount, String refId) {
        try {
            if (paymentRefId == null || paymentRefId.isBlank()) {
                return "Missing transaction identifier from payment callback.";
            }

            Optional<Order> orderOpt = orderRepository.findByPaymentRefId(paymentRefId);
            if (!orderOpt.isPresent()) {
                orderOpt = orderRepository.findByPaymentGatewayRefId(paymentRefId);
            }
            if (!orderOpt.isPresent()) {
                try {
                    Long orderId = Long.parseLong(paymentRefId);
                    orderOpt = orderRepository.findById(orderId);
                } catch (NumberFormatException ignored) {
                    // Ignore if not numeric
                }
            }
            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                double paidAmount = order.getTotalAmount();
                if (totalAmount != null && !totalAmount.isBlank()) {
                    try {
                        paidAmount = Double.parseDouble(totalAmount);
                    } catch (NumberFormatException nfe) {
                        return "Invalid payment amount received: " + totalAmount;
                    }
                }
                if (Double.compare(paidAmount, order.getTotalAmount()) != 0) {
                    return "Payment amount mismatch. Expected " + order.getTotalAmount() + " but got " + paidAmount;
                }
                order.setOrderStatus("COMPLETED");
                order.setPaymentMethod("ESEWA");
                order.setPaymentRefId(paymentRefId);
                if (refId != null && !refId.isBlank()) {
                    order.setPaymentGatewayRefId(refId);
                }
                orderRepository.save(order);

                return "Payment successful! Order ID: " + order.getOrderId();
            }
            return "Order not found for transaction: " + paymentRefId;
        } catch (Exception e) {
            return "Payment processing error: " + e.getMessage();
        }
    }

    /**
     * Process payment failure callback by payment reference ID
     */
    public String processFailureByPaymentRefId(String paymentRefId) {
        try {
            Optional<Order> orderOpt = orderRepository.findByPaymentRefId(paymentRefId);

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                order.setOrderStatus("PAYMENT_FAILED");
                orderRepository.save(order);

                return "Payment cancelled for Order ID: " + order.getOrderId();
            }
            return "Order not found";
        } catch (Exception e) {
            return "Error processing cancellation: " + e.getMessage();
        }
    }

    /**
     * Simple verification stub for backward compatibility
     */
    public boolean verifyPayment(Order order) {
        // Check transaction status with eSewa
        Map<String, Object> status = checkTransactionStatus(
                String.valueOf(order.getOrderId()),
                order.getTotalAmount());

        // If status is COMPLETE, payment is verified
        if (status.containsKey("status") && "COMPLETE".equals(status.get("status"))) {
            order.setOrderStatus("COMPLETED");
            order.setPaymentMethod("ESEWA");
            if (status.containsKey("ref_id")) {
                order.setPaymentGatewayRefId((String) status.get("ref_id"));
            }
            orderRepository.save(order);
            return true;
        }

        return false;
    }

    /**
     * Generate payment reference ID
     */
    public String generatePaymentReferenceId() {
        return "ORD-" + System.currentTimeMillis();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public String getFailureUrl() {
        return failureUrl;
    }
}