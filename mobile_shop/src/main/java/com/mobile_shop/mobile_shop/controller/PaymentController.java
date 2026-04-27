package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.service.OrderService;
import com.mobile_shop.mobile_shop.service.CartService;
import com.mobile_shop.mobile_shop.service.ProductService;
import com.mobile_shop.mobile_shop.service.PaymentService;
import com.mobile_shop.mobile_shop.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/payment")
public class PaymentController {

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

            // Add payment data to model for the form
            model.addAttribute("orderId", orderId);
            model.addAttribute("amount", String.format("%.2f", order.getTotalAmount()));
            model.addAttribute("totalAmount", String.format("%.2f", order.getTotalAmount()));
            model.addAttribute("transactionUuid", String.valueOf(orderId));
            model.addAttribute("productCode", paymentService.getMerchantCode());
            model.addAttribute("successUrl", paymentService.getSuccessUrl());
            model.addAttribute("failureUrl", paymentService.getFailureUrl());
            model.addAttribute("signature", paymentService.generateSignature(orderId, order.getTotalAmount()));

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
     * eSewa redirects here with: ?oid=ORDER_ID&amt=AMOUNT&refId=REFERENCE_ID
     */
    @GetMapping("/success")
    public String paymentSuccess(
            @RequestParam String oid,
            @RequestParam String amt,
            @RequestParam(required = false) String refId,
            Model model) {

        // Process the success
        String result = paymentService.processSuccess(oid, amt, refId);

        Optional<Order> orderOpt = orderService.getOrderById(Long.parseLong(oid));
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            model.addAttribute("order", order);
        }

        model.addAttribute("orderId", oid);
        model.addAttribute("refId", refId != null ? refId : "N/A");
        model.addAttribute("success", "Payment successful! Your order has been placed.");
        return "customer/payment-success";
    }

    /**
     * Handle payment failure callback from eSewa
     */
    @GetMapping("/failure")
    public String paymentFailure(@RequestParam String oid, Model model) {
        // Process the failure
        paymentService.processFailure(oid);

        Optional<Order> orderOpt = orderService.getOrderById(Long.parseLong(oid));
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            model.addAttribute("order", order);
        }

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