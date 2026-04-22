package com.mobile_shop.mobile_shop.controller;

import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.entity.ProductView;
// import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.repository.ProductRepository;
import com.mobile_shop.mobile_shop.repository.ProductViewRepository;
import com.mobile_shop.mobile_shop.repository.CustomerRepository;
// import com.mobile_shop.mobile_shop.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
// import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Objects;

@Controller
@RequestMapping("/dashboard")
public class ProductController {

    // @Autowired
    // private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductViewRepository productViewRepository;

    @Autowired
    private CustomerRepository customerRepository;

    // Get product image
    @GetMapping("/image/{id}")
    public ResponseEntity<ByteArrayResource> getProductImage(@PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);

        if (product.isPresent() && product.get().getImageData() != null) {
            byte[] imageData = product.get().getImageData();
            ByteArrayResource resource = new ByteArrayResource(imageData);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .contentLength(imageData.length)
                    .body(resource);
        }

        return ResponseEntity.notFound().build();
    }

    // Product detail page
    @GetMapping("/{id}")
    public String productDetail(@PathVariable Long id,
            Authentication authentication,
            Model model) {
        System.out.println("ProductController.productDetail called with id: " + id);
        Optional<Product> productOpt = productRepository.findById(id);
        System.out.println("Product found: " + productOpt.isPresent());

        if (productOpt.isEmpty()) {
            // product missing – redirect to dashboard products listing
            System.out.println("Product not found, redirecting to /dashboard/products");
            return "redirect:/dashboard/products";
        }

        Product product = productOpt.get();
        System.out.println("Product: " + product.getProductName() + ", id: " + product.getProductId());

        // Track product view if user is logged in
        if (authentication != null && authentication.isAuthenticated()) {
            customerRepository.findByEmail(authentication.getName())
                    .ifPresent(customer -> {
                        try {
                            ProductView view = new ProductView();
                            view.setProduct(product);
                            view.setCustomer(customer);
                            productViewRepository.save(view);
                        } catch (Exception e) {
                            // ignore exceptions to prevent 500 error
                        }
                    });
        }

        // Get related products
        List<Product> relatedProducts = productRepository.findRelatedProducts(id, product.getCategory(),
                product.getBrand());

        // Filter out any null products to prevent Thymeleaf errors
        relatedProducts = relatedProducts.stream().filter(Objects::nonNull).collect(Collectors.toList());

        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);

        // if user logged in include saved shipping address for convenience
        if (authentication != null && authentication.isAuthenticated()) {
            customerRepository.findByEmail(authentication.getName())
                    .map(c -> c.getShippingAddress())
                    .ifPresent(addr -> model.addAttribute("savedAddress", addr));
        }

        // template located at templates/customer/product-detail.html
        return "customer/product-detail";
    }

    // Products list page
    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productRepository.findByStatus("ACTIVE"));
        model.addAttribute("trendingProducts", productRepository.findTop10ByOrderBySoldStockDesc());
        // view lives under customer directory
        return "customer/products";
    }

    // Search products
    @GetMapping("/search")
    public String search(@RequestParam String query, Model model) {
        model.addAttribute("products", productRepository.findByProductNameContainingIgnoreCase(query));
        return "products";
    }
}