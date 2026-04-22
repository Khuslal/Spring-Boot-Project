package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatus(String status);

    List<Product> findByCategory(String category);

    List<Product> findByBrand(String brand);

    List<Product> findTop10ByOrderBySoldStockDesc();

    List<Product> findByProductNameContainingIgnoreCase(String name);

    @Query("SELECT p FROM Product p WHERE p.productId != :productId AND (p.category = :category OR p.brand = :brand)")
    List<Product> findRelatedProducts(Long productId, String category, String brand);
}