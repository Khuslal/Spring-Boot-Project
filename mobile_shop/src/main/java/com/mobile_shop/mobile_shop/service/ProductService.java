package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public Product addProduct(Product product, MultipartFile imageFile) throws IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            product.setImageData(imageFile.getBytes());
        }
        product.setStatus("ACTIVE");
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Product product, MultipartFile imageFile) throws IOException {
        Optional<Product> existing = productRepository.findById(id);
        if (existing.isPresent()) {
            Product p = existing.get();
            p.setProductName(product.getProductName());
            p.setBrand(product.getBrand());
            p.setCategory(product.getCategory());
            p.setPrice(product.getPrice());
            p.setAvailableStock(product.getAvailableStock());
            p.setDescription(product.getDescription());
            p.setFeatures(product.getFeatures());

            if (imageFile != null && !imageFile.isEmpty()) {
                p.setImageData(imageFile.getBytes());
            }

            return productRepository.save(p);
        }
        return null;
    }

    public void updateProductStatus(Long id, String status) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setStatus(status);
            productRepository.save(product);
        }
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getActiveProducts() {
        return productRepository.findByStatus("ACTIVE");
    }

    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    public List<Product> getProductsByBrand(String brand) {
        return productRepository.findByBrand(brand);
    }

    public List<Product> getTrendingProducts() {
        return productRepository.findTop10ByOrderBySoldStockDesc();
    }

    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    public Product updateStock(Long id, int quantity) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.setAvailableStock(product.getAvailableStock() - quantity);
            product.setSoldStock(product.getSoldStock() + quantity);
            return productRepository.save(product);
        }
        return null;
    }
}