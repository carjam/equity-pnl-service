package com.companyx.equity.controller;

import com.companyx.equity.dto.AuthRequest;
import com.companyx.equity.dto.AuthResponse;
import com.companyx.equity.security.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login attempt for user: {}", request.getUid());
        
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUid(), request.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUid());
        final String jwt = jwtUtil.generateToken(userDetails);

        log.info("Login successful for user: {}", request.getUid());
        return ResponseEntity.ok(new AuthResponse(jwt, request.getUid()));
    }
}
