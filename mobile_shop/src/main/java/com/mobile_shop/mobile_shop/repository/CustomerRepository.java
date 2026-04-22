package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Customer> findByIsActive(Boolean isActive);
}