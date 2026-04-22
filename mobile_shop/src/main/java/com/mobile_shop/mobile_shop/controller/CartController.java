package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.repository.CustomerRepository;
import com.mobile_shop.mobile_shop.repository.ProductRepository;
import com.mobile_shop.mobile_shop.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping
    public String viewCart(Model model, Authentication authentication) {
        // protect against unauthenticated access, though security should already
        // enforce it. this prevents model attributes being absent and causing
        // template errors.
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login?redirect=/cart";
        }

        List<CartItem> cartItems = new ArrayList<>();
        Double totalAmount = 0.0;
        Integer itemCount = 0;

        Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
        if (customer != null) {
            cartItems = cartService.getCartItems(customer);
            totalAmount = cartService.getCartTotal(customer);
            itemCount = cartItems.stream().mapToInt(CartItem::getQuantity).sum();
        }

        model.addAttribute("cartItems", cartItems);
        model.addAttribute("totalAmount", totalAmount);
        model.addAttribute("itemCount", itemCount);

        return "customer/myCart";
    }

    @PostMapping("/add")
    public String addToCart(@RequestParam Long productId,
            @RequestParam Integer quantity,
            Authentication authentication) {
        if (authentication != null) {
            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            Product product = productRepository.findById(productId).orElse(null);

            if (customer != null && product != null) {
                cartService.addToCart(customer, product, quantity);
            }
        }
        // after adding to cart, send user back to the product detail under the
        // new /dashboard path (old /product endpoint was removed)
        return "redirect:/dashboard/" + productId;
    }

    @PostMapping("/update")
    public String updateCart(@RequestParam Long cartItemId,
            @RequestParam Integer quantity) {
        cartService.updateQuantity(cartItemId, quantity);
        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String removeFromCart(@RequestParam Long cartItemId) {
        cartService.removeItem(cartItemId);
        return "redirect:/cart";
    }

    @PostMapping("/clear")
    public String clearCart(Authentication authentication) {
        if (authentication != null) {
            Customer customer = customerRepository.findByEmail(authentication.getName()).orElse(null);
            if (customer != null) {
                cartService.clearCart(customer);
            }
        }
        return "redirect:/cart";
    }
}