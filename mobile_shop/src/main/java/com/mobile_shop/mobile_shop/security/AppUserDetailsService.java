package com.mobile_shop.mobile_shop.security;

import com.mobile_shop.mobile_shop.service.AdminService;
import com.mobile_shop.mobile_shop.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AdminService adminService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            // attempt to load as customer first (lookup by email)
            return customerService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            // not a customer, try admin
        }

        try {
            return adminService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            // combine both messages
            throw new UsernameNotFoundException("User not found: " + username);
        }
    }
}