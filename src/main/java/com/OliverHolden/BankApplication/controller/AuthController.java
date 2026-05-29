package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.dto.request.LoginRequest;
import com.OliverHolden.BankApplication.dto.response.LoginResponse;
import com.OliverHolden.BankApplication.security.CustomUserPrincipal;
import com.OliverHolden.BankApplication.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "auth", description = "Authenticate a user")
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Login and receive a JWT token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        CustomUserPrincipal principal = (CustomUserPrincipal) authentication.getPrincipal();
        JwtTokenProvider.TokenResult result = jwtTokenProvider.generateToken(principal.getId());

        log.info("Login successful for userId: {}", principal.getId());
        return ResponseEntity.ok(LoginResponse.builder()
                .token(result.token())
                .tokenType("Bearer")
                .expiresAt(result.expiresAt())
                .build());
    }
}
