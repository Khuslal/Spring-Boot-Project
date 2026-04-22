# MobileShop Application

This is a Spring Boot web application built with:

- **Spring Boot 3 / Java 25**
- **Thymeleaf** for server-rendered HTML
- **Spring Security** for authentication/authorization
- **Spring Data JPA (Hibernate)** with **MySQL** as the database
- **Spring MVC** / **Web** starter with JSON support
- Static assets including CSS/JS under `src/main/resources/static`

The project is already wired to the existing HTML templates located under
`src/main/resources/templates` (customer and admin pages).

## Features

- Customer and administrator login pages with one-time authorization codes
- Role-based access control: `/admin/**` is restricted to `ROLE_ADMIN`
- Registration form storing customers in the database
- JPA entities (`User`, `Product`, `Order`, `CartItem`) automatically create
  corresponding tables when the application starts (see `spring.jpa.hibernate.ddl-auto`)
- Passwords are encrypted with BCrypt

## Database Setup

1. Ensure MySQL is running and reachable.
2. Execute the following once to create the database:
   ```sql
   DROP DATABASE IF EXISTS mobile_shop;
   CREATE DATABASE mobile_shop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   -- grant privileges as needed
   ```
3. Update `src/main/resources/application.properties` with your database
   credentials if they differ from the defaults (`root`/`password`).

Hibernate will drop and recreate tables on each startup (`ddl-auto=create` by
default). Change to `update` once you have persisted data you want to keep.

## Running the Application

Use the Maven wrapper from the `mobile_shop` module:

```powershell
cd "c:\Users\Khuslal\OneDrive\Desktop\All Java Projects\mobile_shop\mobile_shop"
./mvnw spring-boot:run
```

Browse to `http://localhost:8080`:

- Customer login: `/login`
- Admin login: `/admin/login`
- Registration: `/register`

### Authentication Flow

- Forms post to `/login` with fields `email`, `password`, optional `admin` and
  `authCode`/`authRole` parameters.
- A `JpaUserDetailsService` loads user details from the `user` table.
- A custom success handler redirects admins to `/admin/dashboard` and others
  to `/`.
- A failure handler reroutes back to the correct login page based on the
  `admin` parameter.

## Building

Compile and package with Maven:

```powershell
./mvnw clean package
```

The resulting JAR is under `target/mobile_shop-0.0.1-SNAPSHOT.jar`.

## Development Notes

- Entities and repositories are defined under `com.mobile_shop.mobile_shop.entity`
  and `repository` respectively; add new ones as needed.
- Controllers live in `com.mobile_shop.mobile_shop.controller`.
- Security configuration is in `com.mobile_shop.mobile_shop.config.SecurityConfig`.
- Static resources are in `src/main/resources/static`.

## Next Steps

- Implement business logic for products, cart, orders, etc.
- Add JSON REST endpoints if needed (already supported by `@RestController`).
- Enhance validation and user feedback.
- Replace `ddl-auto=create` with `update` or use migrations (Flyway/Liquibase).

Enjoy developing! Feel free to ask for further customization or assistance.
