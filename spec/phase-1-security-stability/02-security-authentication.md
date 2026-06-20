# Security & Authentication Specification

## Objective
Implement comprehensive authentication and authorization using Spring Security with JWT tokens, replacing the current insecure uid-based access control.

## Current State

### Security Issues
- No authentication mechanism - endpoints are publicly accessible
- `uid` passed as query parameter and blindly trusted
- No authorization checks
- No rate limiting
- No HTTPS enforcement
- Database connections without SSL

## Target State

### Architecture Overview

```
Client → API Gateway → JWT Filter → Controller → Service
                ↓
         JWT Validation
         Role Checking
         Rate Limiting
```

## Implementation Plan

### Step 1: Add Dependencies

```xml
<dependencies>
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.5</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Rate Limiting -->
    <dependency>
        <groupId>com.bucket4j</groupId>
        <artifactId>bucket4j-core</artifactId>
        <version>8.10.1</version>
    </dependency>
</dependencies>
```

### Step 2: Create Security Configuration

**File: `src/main/java/com/companyx/equity/config/SecurityConfig.java`**

```java
package com.companyx.equity.config;

import com.companyx.equity.security.JwtAuthenticationEntryPoint;
import com.companyx.equity.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### Step 3: Create JWT Utility

**File: `src/main/java/com/companyx/equity/security/JwtUtil.java`**

```java
package com.companyx.equity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

### Step 4: Create JWT Authentication Filter

**File: `src/main/java/com/companyx/equity/security/JwtAuthenticationFilter.java`**

```java
package com.companyx.equity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userUid;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userUid = jwtUtil.extractUsername(jwt);

            if (userUid != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userUid);

                if (jwtUtil.validateToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
```

### Step 5: Create UserDetailsService Implementation

**File: `src/main/java/com/companyx/equity/security/UserDetailsServiceImpl.java`**

```java
package com.companyx.equity.security;

import com.companyx.equity.model.User;
import com.companyx.equity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String uid) throws UsernameNotFoundException {
        User user = userRepository.findByUid(uid)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + uid));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUid())
                .password(user.getPassword()) // Add password field to User entity
                .roles(user.getRole()) // Add role field to User entity
                .build();
    }
}
```

### Step 6: Update User Entity

**File: `src/main/java/com/companyx/equity/model/User.java`**

```java
package com.companyx.equity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    private String firstName;
    private String lastName;
    
    @NotNull
    @Column(unique = true)
    private String uid;
    
    @NotNull
    private String password;
    
    @NotNull
    private String role; // ROLE_USER, ROLE_ADMIN
    
    private boolean enabled = true;
}
```

### Step 7: Create Authentication Controller

**File: `src/main/java/com/companyx/equity/controller/AuthController.java`**

```java
package com.companyx.equity.controller;

import com.companyx.equity.dto.AuthRequest;
import com.companyx.equity.dto.AuthResponse;
import com.companyx.equity.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUid(), request.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUid());
        final String jwt = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(new AuthResponse(jwt));
    }
}
```

### Step 8: Update Controllers to Use Security Context

**File: `src/main/java/com/companyx/equity/controller/TransactionController.java`**

```java
package com.companyx.equity.controller;

import com.companyx.equity.model.Position;
import com.companyx.equity.model.Transaction;
import com.companyx.equity.service.PnLService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {
    
    private final PnLService pnLService;

    @GetMapping("/pnl")
    public EntityModel<Map<String, Position>> pnlBetween(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        String uid = authentication.getName();
        return EntityModel.of(pnLService.getPositions(uid, from, to));
    }

    @GetMapping("/transactions/{id}")
    public EntityModel<Transaction> show(
            Authentication authentication,
            @PathVariable String id
    ) {
        String uid = authentication.getName();
        return EntityModel.of(pnLService.getTransactionById(uid, id));
    }

    @GetMapping("/transactions")
    public List<Transaction> findBetween(
            Authentication authentication,
            @RequestParam Optional<@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate> from,
            @RequestParam Optional<@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate> to
    ) {
        String uid = authentication.getName();
        return pnLService.getTransactionsByDates(uid, from, to);
    }
}
```

### Step 9: Add Configuration Properties

**File: `src/main/resources/application.properties`**

```properties
# JWT Configuration
jwt.secret=${JWT_SECRET}
jwt.expiration=86400000

# Security
spring.security.user.name=admin
spring.security.user.password=admin
```

### Step 10: Database Migration

**File: `src/main/resources/db/migration/V1.2__AddUserSecurity.sql`**

```sql
ALTER TABLE user 
ADD COLUMN password VARCHAR(255) NOT NULL DEFAULT '$2a$10$slYQmi1U7Y0TQPWJ8YMYGe7LZE.qC.JrG3OX8TFj3cTqNLBYv9Yc6',
ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'ROLE_USER',
ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE,
ADD UNIQUE INDEX idx_uid (uid);

-- Default password is 'password' (BCrypt encoded)
-- Update existing users
UPDATE user SET 
    password = '$2a$10$slYQmi1U7Y0TQPWJ8YMYGe7LZE.qC.JrG3OX8TFj3cTqNLBYv9Yc6',
    role = 'ROLE_USER',
    enabled = TRUE;
```

## SSL/TLS Configuration

### Database SSL

```properties
spring.datasource.url=jdbc:mysql://equity-db:3306/equity?useSSL=true&requireSSL=true
spring.datasource.hikari.connection-test-query=SELECT 1
```

### Application HTTPS

```properties
server.ssl.enabled=true
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-password=${KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=equity-pnl
```

## Rate Limiting

**File: `src/main/java/com/companyx/equity/config/RateLimitConfig.java`**

```java
package com.companyx.equity.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    @Bean
    public Map<String, Bucket> rateLimitBuckets() {
        return new ConcurrentHashMap<>();
    }

    public Bucket resolveBucket(String key) {
        return rateLimitBuckets().computeIfAbsent(key, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
```

## Acceptance Criteria

- [ ] All endpoints require authentication (except /auth/login and health check)
- [ ] JWT tokens generated and validated correctly
- [ ] User credentials stored securely (BCrypt)
- [ ] Authentication failures return 401 Unauthorized
- [ ] Authorization failures return 403 Forbidden
- [ ] Rate limiting prevents abuse (100 requests/minute per user)
- [ ] Database connections use SSL
- [ ] All passwords removed from configuration files
- [ ] JWT secret stored in environment variable
- [ ] Security tests pass

## Testing

```bash
# Test authentication
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"uid":"carjam","password":"password"}'

# Test protected endpoint without token (should fail)
curl http://localhost:8080/api/v1/pnl?from=2020-01-01&to=2021-01-01

# Test protected endpoint with token (should succeed)
curl -H "Authorization: Bearer <token>" \
  http://localhost:8080/api/v1/pnl?from=2020-01-01&to=2021-01-01
```

## Dependencies

- 01-dependency-upgrades.md (must complete first)

## Estimated Effort

- Security configuration: 2 days
- JWT implementation: 2 days
- Controller updates: 1 day
- Database migration: 0.5 days
- Testing & validation: 1.5 days
- **Total: 7 days**

## References

- [Spring Security Documentation](https://docs.spring.io/spring-security/reference/index.html)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
