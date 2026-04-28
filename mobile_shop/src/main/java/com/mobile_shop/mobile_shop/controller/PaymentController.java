package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.service.OrderService;
import com.mobile_shop.mobile_shop.service.CartService;
import com.mobile_shop.mobile_shop.service.ProductService;
import com.mobile_shop.mobile_shop.service.PaymentService;
import com.mobile_shop.mobile_shop.repository.CustomerRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PaymentService paymentService;

    /**
     * Handle cart checkout - creates order from cart items and redirects to eSewa
     */
    @GetMapping("/process")
    public String processCartPayment(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login?redirect=/cart";
        }

        Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
        if (customer == null) {
            return "redirect:/login";
        }

        List<CartItem> cartItems = cartService.getCartItems(customer);
        if (cartItems.isEmpty()) {
            return "redirect:/cart?error=empty";
        }

        // Create order from cart items
        Order order = orderService.createOrder(customer, cartItems,
                customer.getShippingAddress() != null ? customer.getShippingAddress() : "");

        // Generate payment reference ID
        order.setPaymentRefId(paymentService.generatePaymentReferenceId());
        order = orderService.save(order);

        // Clear cart after order created
        cartService.clearCart(customer);

        // Show eSewa payment form for the new order
        return "redirect:/payment/process/" + order.getOrderId();
    }

    /**
     * Handle individual order payment and show eSewa payment form
     */
    @GetMapping("/process/{orderId}")
    public String processOrderPayment(@PathVariable Long orderId, Model model) {
        try {
            Optional<Order> orderOpt = orderService.getOrderById(orderId);
            if (orderOpt.isEmpty()) {
                return "redirect:/orders?error=order_not_found";
            }

            Order order = orderOpt.get();

            // Generate a fresh transaction UUID for this payment attempt and persist it on
            // the order
            String transactionUuid = paymentService.generatePaymentReferenceId();
            order.setPaymentRefId(transactionUuid);
            orderService.save(order);

            model.addAttribute("orderId", orderId);
            model.addAttribute("amount", String.format("%.2f", order.getTotalAmount()));
            model.addAttribute("totalAmount", String.format("%.2f", order.getTotalAmount()));
            model.addAttribute("transactionUuid", transactionUuid);
            model.addAttribute("productCode", paymentService.getMerchantCode());
            model.addAttribute("successUrl", paymentService.getSuccessUrl());
            model.addAttribute("failureUrl", paymentService.getFailureUrl());
            model.addAttribute("signature", paymentService.generateSignature(order.getTotalAmount(), transactionUuid));

            // Return the payment form view
            return "customer/esewa-pay";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Payment processing error: " + e.getMessage());
            return "error";
        }
    }

    /**
     * Handle payment success callback from eSewa
     */
    @RequestMapping(value = "/success", method = { RequestMethod.GET, RequestMethod.POST })
    public String paymentSuccess(HttpServletRequest request, Model model) {
        try {
            String oid = getParameter(request, "oid", "transaction_uuid", "transactionUuid");
            String amt = getParameter(request, "amt", "total_amount", "amount");
            String refId = getParameter(request, "refId", "ref_id", "reference_id");
            String status = getParameter(request, "status", "payment_status");
            String data = getParameter(request, "data");

            logger.info("Raw parameters - oid: {}, amt: {}, refId: {}, status: {}, data: {}", oid, amt, refId, status,
                    data != null ? "present" : "null");

            String transactionUuid = oid;
            String totalAmount = amt;

            if (data != null && !data.isBlank()) {
                Map<String, String> parsed = parseEsewaData(data);
                logger.info("Parsed eSewa data: {}", parsed);
                if (parsed.containsKey("transaction_uuid")) {
                    transactionUuid = parsed.get("transaction_uuid");
                }
                if (parsed.containsKey("total_amount")) {
                    totalAmount = parsed.get("total_amount");
                }
                if (parsed.containsKey("ref_id")) {
                    refId = parsed.get("ref_id");
                }
                if (parsed.containsKey("status")) {
                    status = parsed.get("status");
                }
            }

            if (transactionUuid == null || transactionUuid.isBlank()) {
                transactionUuid = getParameter(request, "transaction_uuid", "oid", "orderId");
            }
            if (totalAmount == null || totalAmount.isBlank()) {
                totalAmount = getParameter(request, "amt", "amount", "total_amount");
            }
            if (status == null || status.isBlank()) {
                status = "COMPLETED";
            }

            logger.info(
                    "Payment success callback received: transactionUuid={}, totalAmount={}, refId={}, status={}",
                    transactionUuid, totalAmount, refId, status);

            String result = paymentService.processSuccessByPaymentRefId(transactionUuid, totalAmount, refId);
            logger.info("Payment processing result: {}", result);

            Optional<Order> orderOpt = orderService.getOrderByPaymentRefId(transactionUuid);
            if (!orderOpt.isPresent()) {
                orderOpt = orderService.getOrderByPaymentGatewayRefId(transactionUuid);
            }
            if (!orderOpt.isPresent()) {
                try {
                    Long orderId = Long.parseLong(transactionUuid);
                    orderOpt = orderService.getOrderById(orderId);
                } catch (NumberFormatException ignored) {
                    // transactionUuid was not a numeric order ID, ignore
                }
            }

            if (orderOpt.isPresent()) {
                Order order = orderOpt.get();
                logger.info("Order found: orderId={}, status={}, items={}", order.getOrderId(), order.getOrderStatus(),
                        order.getOrderItems() != null ? order.getOrderItems().size() : 0);
                model.addAttribute("order", order);
            } else {
                logger.warn("No order found for transactionUuid: {}", transactionUuid);
            }

            model.addAttribute("displayOrderNumber",
                    orderOpt.map(order -> order.getOrderId() != null ? order.getOrderId().toString() : null)
                            .orElse(transactionUuid != null ? transactionUuid : "N/A"));
            model.addAttribute("displayOrderDate",
                    orderOpt.map(order -> order.getOrderDate() != null ? order.getOrderDate().toString() : "N/A")
                            .orElse("N/A"));
            model.addAttribute("displayCustomerEmail",
                    orderOpt.map(order -> order.getCustomer() != null ? order.getCustomer().getEmail() : "N/A")
                            .orElse("N/A"));
            model.addAttribute("displayShippingAddress",
                    orderOpt.map(order -> order.getShippingAddress() != null ? order.getShippingAddress() : "N/A")
                            .orElse("N/A"));
            model.addAttribute("displayTransactionUuid", transactionUuid != null ? transactionUuid : "N/A");
            model.addAttribute("displayPaymentReference", refId != null ? refId : "N/A");
            model.addAttribute("displayPaymentMethod", "eSewa");
            model.addAttribute("displayAmount", totalAmount != null ? ("NPR " + totalAmount) : "NPR 0.00");
            model.addAttribute("displayPaymentStatus", status != null ? status : "COMPLETED");
            model.addAttribute("displayOrderStatus",
                    orderOpt.map(Order::getOrderStatus).orElse("PROCESSING"));
            model.addAttribute("success", result);

            logger.info("Rendering payment success page with attributes: orderNumber={}, paymentStatus={}",
                    model.asMap().get("displayOrderNumber"), model.asMap().get("displayPaymentStatus"));
            return "customer/payment-success";
        } catch (Exception e) {
            logger.error("Error processing payment success callback", e);
            e.printStackTrace();
            model.addAttribute("status", 500);
            model.addAttribute("message",
                    "An error occurred while processing your payment confirmation. Please contact support. Error: "
                            + e.getMessage());
            return "error";
        }
    }

    private Map<String, String> parseEsewaData(String data) {
        try {
            String payload = data;
            if (!payload.trim().startsWith("{") && !payload.trim().startsWith("[")) {
                try {
                    byte[] decoded = Base64.getDecoder().decode(payload.replace(' ', '+'));
                    payload = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Failed to Base64 decode eSewa data payload; trying JSON parse directly", ex);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(payload, new TypeReference<HashMap<String, String>>() {
            });
        } catch (Exception e) {
            logger.warn("Unable to parse eSewa callback data payload", e);
            return new HashMap<>();
        }
    }

    private String getParameter(HttpServletRequest request, String... names) {
        for (String name : names) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Handle payment failure callback from eSewa
     */
    @RequestMapping(value = "/failure", method = { RequestMethod.GET, RequestMethod.POST })
    public String paymentFailure(HttpServletRequest request, Model model) {
        String transactionUuid = getParameter(request, "oid", "transaction_uuid", "transactionUuid");
        String refId = getParameter(request, "refId", "ref_id", "reference_id");
        String status = getParameter(request, "status", "payment_status");
        String data = getParameter(request, "data");

        if (data != null && !data.isBlank()) {
            Map<String, String> parsed = parseEsewaData(data);
            if (parsed.containsKey("transaction_uuid")) {
                transactionUuid = parsed.get("transaction_uuid");
            }
            if (parsed.containsKey("ref_id")) {
                refId = parsed.get("ref_id");
            }
            if (parsed.containsKey("status")) {
                status = parsed.get("status");
            }
        }
        if (transactionUuid == null || transactionUuid.isBlank()) {
            transactionUuid = getParameter(request, "orderId", "transaction_uuid", "oid");
        }
        if (status == null || status.isBlank()) {
            status = "FAILED";
        }

        logger.debug("Payment failure callback received: transactionUuid={}, refId={}, status={}, dataPresent={}",
                transactionUuid, refId, status, data != null);

        paymentService.processFailureByPaymentRefId(transactionUuid);

        Optional<Order> orderOpt = orderService.getOrderByPaymentRefId(transactionUuid);
        if (orderOpt.isPresent()) {
            model.addAttribute("order", orderOpt.get());
        }

        model.addAttribute("refId", refId != null ? refId : "N/A");
        model.addAttribute("paymentStatus", status);
        model.addAttribute("error", "Payment failed. Please try again.");
        return "customer/payment-failed";
    }

    /**
     * Check transaction status manually
     */
    @GetMapping("/status/{orderId}")
    public String checkPaymentStatus(@PathVariable Long orderId, Model model) {
        Optional<Order> orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            Map<String, Object> status = paymentService.checkTransactionStatus(
                    String.valueOf(orderId),
                    order.getTotalAmount());
            model.addAttribute("order", order);
            model.addAttribute("status", status);
        }
        return "payment/status";
    }

    /**
     * Verify payment (for additional security)
     */
    @PostMapping("/verify")
    public String verifyPayment(@RequestParam String orderId, Model model) {
        Optional<Order> orderOpt = orderService.getOrderById(Long.parseLong(orderId));
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            boolean verified = paymentService.verifyPayment(order);

            if (verified) {
                order.setOrderStatus("COMPLETED");
                order.setPaymentMethod("ESEWA");
            } else {
                order.setOrderStatus("PAYMENT_FAILED");
            }
            orderService.save(order);
        }
        return "redirect:/orders";
    }
}