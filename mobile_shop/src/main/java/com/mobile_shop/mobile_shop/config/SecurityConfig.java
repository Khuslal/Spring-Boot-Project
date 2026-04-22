package com.mobile_shop.mobile_shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import com.mobile_shop.mobile_shop.security.AppUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Autowired
        private AppUserDetailsService userDetailsService;

        // ========== ADMIN SECURITY - Order 1 ==========
        @Bean
        @Order(1)
        public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher("/admin/**")
                                .userDetailsService(userDetailsService)
                                .authorizeHttpRequests((requests) -> requests
                                                .requestMatchers("/admin/login", "/admin/register").permitAll()
                                                .anyRequest().hasRole("ADMIN"))
                                .formLogin((form) -> form
                                                .loginPage("/admin/login")
                                                .loginProcessingUrl("/admin/login") // Separate URL for admin
                                                .usernameParameter("email")
                                                .passwordParameter("password")
                                                .successHandler(adminSuccessHandler())
                                                .failureUrl("/admin/login?error=true")
                                                .permitAll())
                                .logout((logout) -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/admin/login?logout=true")
                                                .permitAll())
                                .csrf(csrf -> csrf.disable());

                return http.build();
        }

        // ========== CUSTOMER SECURITY - Order 2 ==========
        @Bean
        @Order(2)
        public SecurityFilterChain customerSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .userDetailsService(userDetailsService)
                                .authorizeHttpRequests((requests) -> requests
                                                .requestMatchers("/cart/**", "/order/**", "/profile", "/orders")
                                                .hasAnyRole("USER", "ADMIN")
                                                .requestMatchers("/", "/home", "/login", "/register",
                                                                "/dashboard/**", "/products", "/css/**", "/js/**",
                                                                "/images/**")
                                                .permitAll()
                                                .anyRequest().authenticated())
                                .formLogin((form) -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .usernameParameter("email")
                                                .passwordParameter("password")
                                                .successHandler(customerSuccessHandler())
                                                .failureUrl("/login?error=true")
                                                .permitAll())
                                .logout((logout) -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .permitAll())
                                .csrf(csrf -> csrf.disable());

                return http.build();
        }

        // ========== ADMIN SUCCESS HANDLER ==========
        @Bean
        public AuthenticationSuccessHandler adminSuccessHandler() {
                return (request, response, authentication) -> {
                        response.sendRedirect("/admin/dashboard");
                };
        }

        // ========== CUSTOMER SUCCESS HANDLER ==========
        // Use SavedRequestAwareAuthenticationSuccessHandler so we respect the original
        // requested URL when Spring Security intercepted the user. We still allow an
        // explicit "redirect" parameter on the login form to override the target.
        @Bean
        public AuthenticationSuccessHandler customerSuccessHandler() {
                return new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler() {
                        {
                                setDefaultTargetUrl("/products");
                        }

                        @Override
                        public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        org.springframework.security.core.Authentication authentication)
                                        throws java.io.IOException, jakarta.servlet.ServletException {
                                String redirect = request.getParameter("redirect");
                                if (redirect != null && !redirect.isEmpty()) {
                                        getRedirectStrategy().sendRedirect(request, response, redirect);
                                } else {
                                        super.onAuthenticationSuccess(request, response, authentication);
                                }
                        }
                };
        }
}