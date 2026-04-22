package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class MainController {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        // render customer login page stored under templates/customer
        return "customer/customer-login";
    }

    @GetMapping("/register")
    public String register() {
        // render customer registration page
        return "customer/customer-register";
    }

    @PostMapping("/register")
    public String registerSubmit(@ModelAttribute Customer customer) {
        if (customerService.existsByEmail(customer.getEmail())) {
            return "redirect:/register?error";
        }
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        customer.setRole("ROLE_USER");
        customerService.save(customer);
        return "redirect:/login?registered";
    }

    // redirect helper: customer-facing products page is implemented in
    // ProductController under "/dashboard/products". many templates link to
    // "/products" so we provide this convenience mapping.
    @GetMapping("/products")
    public String productsRedirect() {
        return "redirect:/dashboard/products";
    }

    @GetMapping("/orders")
    public String ordersRedirect() {
        // convenience mapping so templates can use /orders
        return "redirect:/order/my-orders";
    }

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication) {
        if (authentication != null) {
            Customer customer = customerService.findByEmail(authentication.getName()).orElse(null);
            if (customer != null) {
                model.addAttribute("fullName", customer.getFullName());
                model.addAttribute("email", customer.getEmail());
                model.addAttribute("phone", customer.getPhone());
                model.addAttribute("registrationDate", customer.getRegistrationDate());
            }
        }
        return "customer/customer-profile";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}