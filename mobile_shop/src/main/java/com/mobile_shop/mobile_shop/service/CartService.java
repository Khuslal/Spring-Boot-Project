package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.CartItem;
import com.mobile_shop.mobile_shop.entity.Customer;
import com.mobile_shop.mobile_shop.entity.Product;
import com.mobile_shop.mobile_shop.repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    public CartItem addToCart(Customer customer, Product product, Integer quantity) {
        Optional<CartItem> existingItem = cartItemRepository.findByCustomerAndProduct(customer, product);

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantity);
            return cartItemRepository.save(item);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCustomer(customer);
            newItem.setProduct(product);
            newItem.setQuantity(quantity);
            return cartItemRepository.save(newItem);
        }
    }

    public List<CartItem> getCartItems(Customer customer) {
        return cartItemRepository.findByCustomer(customer);
    }

    public CartItem updateQuantity(Long cartItemId, Integer quantity) {
        Optional<CartItem> itemOpt = cartItemRepository.findById(cartItemId);
        if (itemOpt.isPresent()) {
            CartItem item = itemOpt.get();
            item.setQuantity(quantity);
            return cartItemRepository.save(item);
        }
        return null;
    }

    public void removeItem(Long cartItemId) {
        cartItemRepository.deleteById(cartItemId);
    }

    public void clearCart(Customer customer) {
        List<CartItem> items = cartItemRepository.findByCustomer(customer);
        cartItemRepository.deleteAll(items);
    }

    public Double getCartTotal(Customer customer) {
        List<CartItem> items = cartItemRepository.findByCustomer(customer);
        return items.stream()
                .mapToDouble(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();
    }
}