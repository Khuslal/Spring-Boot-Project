package com.mobile_shop.mobile_shop.service;

import com.mobile_shop.mobile_shop.entity.Admin;
import com.mobile_shop.mobile_shop.repository.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Optional;

@Service
public class AdminService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public Admin createAdmin(Admin admin) {
        // encode password and ensure role is ADMIN
        admin.setAdminPassword(passwordEncoder.encode(admin.getAdminPassword()));
        admin.setRole("ROLE_ADMIN");
        // if username not set, default to email
        if (admin.getAdminUsername() == null || admin.getAdminUsername().isEmpty()) {
            admin.setAdminUsername(admin.getAdminEmail());
        }
        return adminRepository.save(admin);
    }

    public Optional<Admin> findByUsername(String username) {
        return adminRepository.findByAdminUsername(username);
    }

    public Optional<Admin> findByEmail(String email) {
        return adminRepository.findByAdminEmail(email);
    }

    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByAdminUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found: " + username));

        return new User(
                admin.getAdminUsername(),
                admin.getAdminPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(admin.getRole())));
    }

    public Admin save(Admin admin) {
        return adminRepository.save(admin);
    }
}