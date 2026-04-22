package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;


    @Autowired
    private ProductService productService;

    public Order createOrder(Customer customer, Product product, Integer quantity,
            String shippingAddress, Double totalAmount) {
        Order order = new Order();
        order.setCustomer(customer);
        order.setProduct(product);
        order.setOrderedQuantity(quantity);
        order.setShippingAddress(shippingAddress);
        order.setTotalAmount(totalAmount);
        order.setOrderStatus("PENDING_PAYMENT");

        // Update stock
        productService.updateStock(product.getProductId(), quantity);

        return orderRepository.save(order);
    }

    public List<Order> getCustomerOrders(Customer customer) {
        return orderRepository.findByCustomer(customer);
    }

    public Optional<Order> getOrderById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    public Optional<Order> getOrderByPaymentRefId(String paymentRefId) {
        return orderRepository.findByPaymentRefId(paymentRefId);
    }

    public Order updateOrderStatus(Long orderId, String status) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setOrderStatus(status);
            return orderRepository.save(order);
        }
        return null;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByOrderStatus(status);
    }

    // Additional helper method to save orders
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    // Delete order by ID
    public void deleteOrder(Long orderId) {
        orderRepository.deleteById(orderId);
    }

    public Double getTotalRevenue() {
        Double revenue = orderRepository.getTotalRevenueByStatus("COMPLETED");
        return revenue != null ? revenue : 0.0;
    }

    public Integer getTotalOrderCount() {
        long c = orderRepository.count();
        return c > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) c;
    }
}