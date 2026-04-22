package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Admin;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.service.AdminService;
import com.mobile_shop.mobile_shop.service.ProductService;
import com.mobile_shop.mobile_shop.service.CustomerService;
import com.mobile_shop.mobile_shop.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private OrderService orderService;

    @org.springframework.beans.factory.annotation.Value("${admin.signup.key}")
    private String expectedAdminSignupKey;

    // ========== LOGIN PAGE ==========
    @GetMapping("/login")
    public String adminLogin(Model model) {
        return "admin/admin-login";
    }

    // ========== REGISTER PAGE ==========
    @GetMapping("/register")
    public String showRegisterPage(Model model) {
        model.addAttribute("admin", new Admin());
        return "admin/admin-register";
    }

    // ========== REGISTER SUBMIT ==========
    @PostMapping("/register")
    public String registerAdmin(
            @RequestParam String adminFullname,
            @RequestParam String adminEmail,
            @RequestParam String adminPassword,
            @RequestParam String secretKey,
            Model model) {

        if (adminService.findByEmail(adminEmail).isPresent()) {
            model.addAttribute("error", "Email already registered");
            return "admin/admin-register";
        }

        if (secretKey == null || secretKey.isEmpty() || !secretKey.equals(expectedAdminSignupKey)) {
            model.addAttribute("error", "Invalid admin signup key");
            return "admin/admin-register";
        }

        Admin admin = new Admin();
        admin.setAdminFullname(adminFullname);
        admin.setAdminEmail(adminEmail);
        admin.setAdminPassword(adminPassword);
        admin.setAdminUsername(adminEmail);
        admin.setRole("ROLE_ADMIN");
        admin.setIsActive(true);

        adminService.createAdmin(admin);

        model.addAttribute("success", "Admin registration successful. Please login.");
        return "admin/admin-login";
    }

    // ========== ROOT REDIRECT ==========
    @GetMapping({ "", "/" })
    public String adminRootRedirect(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/admin/dashboard";
        }
        return "redirect:/admin/login";
    }

    // ========== DASHBOARD ==========
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Product> products = productService.getAllProducts();
        List<Customer> customers = customerService.getAllCustomers();
        List<Order> orders = orderService.getAllOrders();

        Double totalRevenue = orderService.getTotalRevenue();
        Integer totalOrders = orderService.getTotalOrderCount();

        model.addAttribute("products", products);
        model.addAttribute("customers", customers);
        model.addAttribute("orders", orders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);
        model.addAttribute("totalOrders", totalOrders != null ? totalOrders : 0);

        return "admin/admin-dashboard";
    }

    // ========== PRODUCTS ==========
    @GetMapping("/products")
    public String manageProducts(Model model) {
        model.addAttribute("products", productService.getAllProducts());
        return "admin/admin-products";
    }

    // ========== PRODUCT FORM (ADD) ==========
    @GetMapping("/product/add")
    public String addProductForm(Model model) {
        model.addAttribute("product", new Product());
        return "admin/product-form";
    }

    @PostMapping("/product/add")
    public String addProductSubmit(
            @ModelAttribute Product product,
            @RequestParam("imageFile") MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {
        try {
            product.setStatus("ACTIVE");
            productService.addProduct(product, imageFile);
            redirectAttributes.addFlashAttribute("success", "Product added successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to add product: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ========== PRODUCT FORM (EDIT) ==========
    @GetMapping("/product/edit/{id}")
    public String editProductForm(@PathVariable Long id, Model model) {
        Optional<Product> product = productService.getProductById(id);
        if (product.isPresent()) {
            model.addAttribute("product", product.get());
        }
        return "admin/product-form";
    }

    @PostMapping("/product/edit/{id}")
    public String editProductSubmit(
            @PathVariable Long id,
            @ModelAttribute Product product,
            @RequestParam("imageFile") MultipartFile imageFile,
            RedirectAttributes redirectAttributes) {
        try {
            productService.updateProduct(id, product, imageFile);
            redirectAttributes.addFlashAttribute("success", "Product updated successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to update product: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ========== DELETE PRODUCT ==========
    @GetMapping("/product/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.deleteProduct(id);
            redirectAttributes.addFlashAttribute("success", "Product deleted successfully!");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Failed to delete product: " + e.getMessage());
        }
        return "redirect:/admin/products";
    }

    // ========== TOGGLE PRODUCT STATUS ==========
    @PostMapping("/product/status")
    public String toggleProductStatus(@RequestParam Long productId, @RequestParam String status) {
        productService.updateProductStatus(productId, status);
        return "redirect:/admin/dashboard";
    }

    // ========== ORDERS ==========
    @GetMapping("/orders")
    public String manageOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        return "admin/admin-orders";
    }

    @PostMapping("/order/status/{id}")
    public String updateOrderStatus(@PathVariable Long id, @RequestParam String status) {
        orderService.updateOrderStatus(id, status);
        return "redirect:/admin/orders?updated";
    }

    @PostMapping("/order/cancel")
    public String cancelOrder(@RequestParam Long orderId) {
        orderService.updateOrderStatus(orderId, "CANCELLED");
        return "redirect:/admin/dashboard";
    }

    // ========== CUSTOMERS ==========
    @GetMapping("/customers")
    public String manageCustomers(Model model) {
        List<Customer> customers = customerService.getAllCustomers();
        Integer totalCustomers = customers.size();
        Integer totalOrders = orderService.getTotalOrderCount();
        Double totalRevenue = orderService.getTotalRevenue();

        model.addAttribute("customers", customers);
        model.addAttribute("totalCustomers", totalCustomers);
        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("totalRevenue", totalRevenue != null ? totalRevenue : 0.0);

        return "admin/admin-customers";
    }

    // ========== PROFILE ==========
    @GetMapping("/profile")
    public String adminProfile(Model model, Authentication authentication) {
        if (authentication != null) {
            String email = authentication.getName();
            adminService.findByEmail(email).ifPresent(admin -> {
                model.addAttribute("fullName", admin.getAdminFullname());
                model.addAttribute("email", admin.getAdminEmail());
            });
        }
        return "admin/admin-profile";
    }
}