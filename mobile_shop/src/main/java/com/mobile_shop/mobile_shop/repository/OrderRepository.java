package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomer(Customer customer);

    List<Order> findByOrderStatus(String status);

    Optional<Order> findByPaymentRefId(String paymentRefId);

    Optional<Order> findByPaymentGatewayRefId(String paymentGatewayRefId);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.orderStatus = :status")
    Double getTotalRevenueByStatus(String status);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderStatus = :status")
    Integer getOrderCountByStatus(String status);

}