package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.repository.OrderRepository;
import com.mobile_shop.mobile_shop.util.EsewaSignatureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
     * Generate complete eSewa payment URL
     * 
     * @param orderId     The order ID
     * @param totalAmount The total amount to pay
     * @return Complete eSewa payment URL
     */
    public String generatePaymentUrl(Long orderId, Double totalAmount) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);

        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Order not found with ID: " + orderId);
        }

        Order order = orderOpt.get();
        Double amountToPay = order.getTotalAmount();

        // Use order ID as transaction UUID (must be alphanumeric and hyphen only)
        String transactionUuid = String.valueOf(orderId);

        // Generate signature using persisted order amount
        String signatureMessage = EsewaSignatureUtil.buildSignatureMessage(
                amountToPay,
                transactionUuid,
                merchantCode);
        String signature = EsewaSignatureUtil.generateSignature(merchantSecret, signatureMessage);

        // Build complete payment URL
        StringBuilder paymentUrl = new StringBuilder();
        paymentUrl.append(baseUrl);
        paymentUrl.append("?amount=")
                .append(URLEncoder.encode(String.format("%.2f", amountToPay), StandardCharsets.UTF_8));
        paymentUrl.append("&tax_amount=0");
        paymentUrl.append("&total_amount=")
                .append(URLEncoder.encode(String.format("%.2f", amountToPay), StandardCharsets.UTF_8));
        paymentUrl.append("&transaction_uuid=").append(URLEncoder.encode(transactionUuid, StandardCharsets.UTF_8));
        paymentUrl.append("&product_code=").append(URLEncoder.encode(merchantCode, StandardCharsets.UTF_8));
        paymentUrl.append("&product_service_charge=0");
        paymentUrl.append("&product_delivery_charge=0");
        paymentUrl.append("&success_url=").append(URLEncoder.encode(successUrl, StandardCharsets.UTF_8));
        paymentUrl.append("&failure_url=").append(URLEncoder.encode(failureUrl, StandardCharsets.UTF_8));
        paymentUrl.append("&signed_field_names=total_amount,transaction_uuid,product_code");
        paymentUrl.append("&signature=").append(URLEncoder.encode(signature, StandardCharsets.UTF_8));

        return paymentUrl.toString();
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

    public Map<String, String> buildEsewaFormFields(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with ID: " + orderId));

        String amount = String.format("%.2f", order.getTotalAmount());
        String transactionUuid = String.valueOf(order.getOrderId());
        String signedFieldNames = "total_amount,transaction_uuid,product_code";
        String signatureMessage = EsewaSignatureUtil.buildSignatureMessage(
                order.getTotalAmount(),
                transactionUuid,
                merchantCode);
        String signature = EsewaSignatureUtil.generateSignature(merchantSecret, signatureMessage);

        Map<String, String> formFields = new HashMap<>();
        formFields.put("amount", amount);
        formFields.put("tax_amount", "0");
        formFields.put("total_amount", amount);
        formFields.put("transaction_uuid", transactionUuid);
        formFields.put("product_code", merchantCode);
        formFields.put("product_service_charge", "0");
        formFields.put("product_delivery_charge", "0");
        formFields.put("success_url", successUrl);
        formFields.put("failure_url", failureUrl);
        formFields.put("signed_field_names", signedFieldNames);
        formFields.put("signature", signature);

        return formFields;
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
     * Process payment success callback
     */
    public String processSuccess(String transactionUuid, String totalAmount, String refId) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(Long.parseLong(transactionUuid));

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                double paidAmount = Double.parseDouble(totalAmount);
                if (Double.compare(paidAmount, order.getTotalAmount()) != 0) {
                    return "Payment amount mismatch. Expected " + order.getTotalAmount() + " but got " + paidAmount;
                }
                order.setOrderStatus("COMPLETED");
                order.setPaymentMethod("ESEWA");
                order.setPaymentRefId(refId != null ? refId : transactionUuid);
                orderRepository.save(order);

                return "Payment successful! Order ID: " + transactionUuid;
            }
            return "Order not found";
        } catch (Exception e) {
            return "Payment processing error: " + e.getMessage();
        }
    }

    /**
     * Process payment failure callback
     */
    public String processFailure(String transactionUuid) {
        try {
            Optional<Order> orderOpt = orderRepository.findById(Long.parseLong(transactionUuid));

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                order.setOrderStatus("PAYMENT_FAILED");
                orderRepository.save(order);

                return "Payment cancelled for Order ID: " + transactionUuid;
            }
            return "Order not found";
        } catch (Exception e) {
            return "Error processing cancellation: " + e.getMessage();
        }
    }

    /**
     * Verify payment signature from eSewa response
     */
    public boolean verifyPaymentSignature(String totalAmount, String transactionUuid,
            String productCode, String receivedSignature) {
        try {
            String signatureMessage = String.format("total_amount=%s,transaction_uuid=%s,product_code=%s",
                    totalAmount, transactionUuid, productCode);

            return EsewaSignatureUtil.verifySignature(merchantSecret, signatureMessage, receivedSignature);
        } catch (Exception e) {
            return false;
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
                order.setPaymentRefId((String) status.get("ref_id"));
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