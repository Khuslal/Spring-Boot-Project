package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Order;
import com.mobile_shop.mobile_shop.entity.OrderItem;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.repository.OrderRepository;
import com.mobile_shop.mobile_shop.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductService productService;

    public Order createOrder(Customer customer, List<CartItem> cartItems,
            String shippingAddress) {
        try {
            Order order = new Order();
            order.setCustomer(customer);
            order.setShippingAddress(shippingAddress);
            order.setOrderStatus("PENDING_PAYMENT");

            List<OrderItem> orderItems = new ArrayList<>();
            Double totalAmount = 0.0;

            for (CartItem cartItem : cartItems) {
                Double productPrice = cartItem.getProduct().getPrice();
                if (productPrice == null) {
                    throw new IllegalArgumentException(
                            "Product price is null for product: " + cartItem.getProduct().getProductId());
                }

                OrderItem orderItem = new OrderItem();
                orderItem.setOrder(order);
                orderItem.setProduct(cartItem.getProduct());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setUnitPrice(productPrice);
                orderItem.setTotalPrice(productPrice * cartItem.getQuantity());

                orderItems.add(orderItem);
                totalAmount += orderItem.getTotalPrice();

                // Update stock
                productService.updateStock(cartItem.getProduct().getProductId(), cartItem.getQuantity());
            }

            order.setOrderItems(orderItems);
            order.setTotalAmount(totalAmount);

            // Save the order first to get the ID
            Order savedOrder = orderRepository.save(order);

            // Update the order reference in each OrderItem and save them
            for (OrderItem orderItem : orderItems) {
                orderItem.setOrder(savedOrder);
                orderItemRepository.save(orderItem);
            }

            return savedOrder;
        } catch (Exception e) {
            System.err.println("Error in createOrder: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
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