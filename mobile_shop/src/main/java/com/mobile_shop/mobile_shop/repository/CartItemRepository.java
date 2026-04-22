package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCustomer(Customer customer);

    Optional<CartItem> findByCustomerAndProduct(Customer customer, com.mobile_shop.mobile_shop.entity.Product product);

    void deleteByCustomer(Customer customer);
}