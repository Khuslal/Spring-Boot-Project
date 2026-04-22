package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.ProductView;
import com.mobile_shop.mobile_shop.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductViewRepository extends JpaRepository<ProductView, Long> {

        List<ProductView> findByCustomer_CustomerId(Long customerId);

        List<ProductView> findByProduct_ProductId(Long productId);
}