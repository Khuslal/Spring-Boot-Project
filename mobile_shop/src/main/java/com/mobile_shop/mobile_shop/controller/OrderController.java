package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.repository.CustomerRepository;
import com.mobile_shop.mobile_shop.repository.ProductRepository;
import com.mobile_shop.mobile_shop.service.CartService;
import com.mobile_shop.mobile_shop.service.OrderService;
import com.mobile_shop.mobile_shop.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/checkout")
    public String checkout(Authentication authentication, Model model) {
        if (authentication != null) {
            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            if (customer != null) {
                List<CartItem> cartItems = cartService.getCartItems(customer);
                Double cartTotal = cartService.getCartTotal(customer);
                model.addAttribute("cartItems", cartItems);
                model.addAttribute("cartTotal", cartTotal);
                model.addAttribute("customer", customer);
            }
        }
        return "checkout";
    }

    @PostMapping("/place")
    public String placeOrder(@RequestParam Long productId,
            @RequestParam Integer quantity,
            @RequestParam String shippingAddress,
            Authentication authentication) {
        try {
            // log incoming request
            System.out.println("OrderController.placeOrder called: productId=" + productId + ", quantity=" + quantity
                    + ", shippingAddress='" + shippingAddress + "'");

            // user must be authenticated to place a direct order
            if (authentication == null || !authentication.isAuthenticated()) {
                // redirect back to product detail so user can login first
                return "redirect:/login?redirect=/dashboard/" + productId;
            }

            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            Product product = productRepository.findById(productId).orElse(null);

            if (customer != null && product != null) {
                // Create a cart item for single product order
                CartItem cartItem = new CartItem();
                cartItem.setCustomer(customer);
                cartItem.setProduct(product);
                cartItem.setQuantity(quantity);

                List<CartItem> cartItems = List.of(cartItem);
                Order order = orderService.createOrder(customer, cartItems, shippingAddress);
                order.setPaymentRefId(paymentService.generatePaymentReferenceId());
                order = orderService.save(order);

                if (order.getOrderId() == null) {
                    throw new RuntimeException("Order ID is null after saving");
                }

                return "redirect:/payment/process/" + order.getOrderId();
            }

            // fall back to cart to avoid exposing order endpoint
            return "redirect:/cart";
        } catch (Exception e) {
            System.err.println("Error in placeOrder: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/error?message=" + e.getMessage();
        }
    }

    // new handler for checking out entire cart
    @PostMapping("/placeCart")
    public String placeCart(@RequestParam String shippingAddress,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (authentication != null) {
            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            if (customer != null) {
                List<CartItem> cartItems = cartService.getCartItems(customer);
                if (!cartItems.isEmpty()) {
                    Order order = orderService.createOrder(customer, cartItems, shippingAddress);
                    order.setPaymentRefId(paymentService.generatePaymentReferenceId());
                    orderService.save(order);

                    // Clear cart after order created
                    cartService.clearCart(customer);
                    redirectAttributes.addFlashAttribute("success",
                            "Order placed successfully! Proceed with payment.");
                    return "redirect:/payment/process/" + order.getOrderId();
                } else {
                    redirectAttributes.addFlashAttribute("error", "Your cart is empty.");
                }
            }
        }
        return "redirect:/cart";
    }

    @GetMapping({ "/my-orders", "/orders" })
    public String myOrders(Authentication authentication, Model model) {
        List<Order> orders = new java.util.ArrayList<>();
        if (authentication != null && authentication.isAuthenticated()) {
            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            if (customer != null) {
                List<Order> fetched = orderService.getCustomerOrders(customer);
                if (fetched != null) {
                    orders = fetched;
                }
            }
        }
        model.addAttribute("orders", orders);
        // customer orders view located at templates/customer/orders.html
        return "customer/orders";
    }

    @GetMapping("/detail/{id}")
    public String orderDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Optional<Order> orderOpt = orderService.getOrderById(id);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            model.addAttribute("order", order);
        }
        return "order-detail";
    }
}