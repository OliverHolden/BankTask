package com.OliverHolden.BankApplication.controller;

import com.OliverHolden.BankApplication.dto.request.CreateUserRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateUserRequest;
import com.OliverHolden.BankApplication.dto.response.UserResponse;
import com.OliverHolden.BankApplication.security.CustomUserPrincipal;
import com.OliverHolden.BankApplication.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Tag(name = "user", description = "Manage a user")
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a new user")
    @SecurityRequirements(value = {})
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid details supplied"),
            @ApiResponse(responseCode = "409", description = "Email already registered"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /v1/users");
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request));
    }

    @Operation(summary = "Fetch user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User details"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable @Pattern(regexp = "^usr-[A-Za-z0-9]+$") String userId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("GET /v1/users/{}", userId);
        return ResponseEntity.ok(userService.getUser(userId, principal.getId()));
    }

    @Operation(summary = "Update user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid details supplied"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Email already registered"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @PatchMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable @Pattern(regexp = "^usr-[A-Za-z0-9]+$") String userId,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("PATCH /v1/users/{}", userId);
        return ResponseEntity.ok(userService.updateUser(userId, request, principal.getId()));
    }

    @Operation(summary = "Delete user by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorised"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "User has existing bank accounts"),
            @ApiResponse(responseCode = "500", description = "Unexpected error")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable @Pattern(regexp = "^usr-[A-Za-z0-9]+$") String userId,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        log.info("DELETE /v1/users/{}", userId);
        userService.deleteUser(userId, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
