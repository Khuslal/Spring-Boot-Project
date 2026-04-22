package com.mobile_shop.mobile_shop.repository;

import com.mobile_shop.mobile_shop.entity.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByAdminUsername(String username);

    Optional<Admin> findByAdminEmail(String email);

    Optional<Admin> findByAdminSignupKey(String signupKey);
}