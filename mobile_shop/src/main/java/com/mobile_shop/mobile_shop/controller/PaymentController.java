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

        Double totalAmount = cartService.getCartTotal(customer);

        // Create order from first cart item
        CartItem firstItem = cartItems.get(0);
        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(firstItem.getProduct());
        order.setOrderedQuantity(firstItem.getQuantity());
        order.setShippingAddress(customer.getShippingAddress() != null ? customer.getShippingAddress() : "");
        order.setTotalAmount(totalAmount);
        order.setOrderStatus("PENDING_PAYMENT");
        order.setPaymentRefId(paymentService.generatePaymentReferenceId());

        order = orderService.save(order);

        // Update product stock for all cart items
        for (CartItem item : cartItems) {
            productService.updateStock(item.getProduct().getProductId(), item.getQuantity());
        }

        // Clear cart after order created
        cartService.clearCart(customer);

        // Generate eSewa payment URL and redirect
        String paymentUrl = paymentService.generatePaymentUrl(order.getOrderId(), order.getTotalAmount());
        return "redirect:" + paymentUrl;
    }

    /**
     * Handle individual order payment
     */
    @GetMapping("/process/{orderId}")
    public String processOrderPayment(@PathVariable Long orderId, Model model) {
        Optional<Order> orderOpt = orderService.getOrderById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            model.addAttribute("order", order);

            // Generate eSewa payment URL
            String paymentUrl = paymentService.generatePaymentUrl(order.getOrderId(), order.getTotalAmount());
            model.addAttribute("paymentUrl", paymentUrl);
        }
        return "payment/process";
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

        model.addAttribute("success", "Payment successful! Your order has been placed.");
        return "payment/success";
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
        return "payment/failure";
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