package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.request.CreateUserRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateUserRequest;
import com.OliverHolden.BankApplication.dto.response.UserResponse;
import com.OliverHolden.BankApplication.exception.ConflictException;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.model.User;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.UserRepository;
import com.OliverHolden.BankApplication.utility.AddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Creating new user");
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String userId = "usr-" + UUID.randomUUID().toString().replace("-", "");
        User user = User.builder()
                .id(userId)
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .address(AddressMapper.toEntity(request.getAddress()))
                .createdTimestamp(now)
                .updatedTimestamp(now)
                .build();
        try {
            return toResponse(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation saving user", e);
            throw new ConflictException("Email already registered");
        }
    }

    public UserResponse getUser(String userId, String principalId) {
        log.info("Fetching user: {}", userId);
        if (!userId.equals(principalId)) {
            log.warn("Forbidden: principal {} attempted to access user {}", principalId, userId);
            throw new ForbiddenException("Access denied");
        }
        return userRepository.findById(userId)
                .map(this::toResponse)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", userId);
                    return new NotFoundException("User not found");
                });
    }

    @Transactional
    public UserResponse updateUser(String userId, UpdateUserRequest request, String principalId) {
        log.info("Updating user: {}", userId);
        if (!userId.equals(principalId)) {
            log.warn("Forbidden: principal {} attempted to update user {}", principalId, userId);
            throw new ForbiddenException("Access denied");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", userId);
                    return new NotFoundException("User not found");
                });
        if (request.getName() != null) user.setName(request.getName());
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new ConflictException("Email already registered");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) user.setPhoneNumber(request.getPhoneNumber());
        if (request.getAddress() != null) user.setAddress(AddressMapper.toEntity(request.getAddress()));
        user.setUpdatedTimestamp(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            return toResponse(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation updating user", e);
            throw new ConflictException("Email already registered");
        }
    }

    @Transactional
    public void deleteUser(String userId, String principalId) {
        log.info("Deleting user: {}", userId);
        if (!userId.equals(principalId)) {
            log.warn("Forbidden: principal {} attempted to delete user {}", principalId, userId);
            throw new ForbiddenException("Access denied");
        }
        if (!userRepository.existsById(userId)) {
            log.warn("User not found for deletion: {}", userId);
            throw new NotFoundException("User not found");
        }
        if (accountRepository.existsByUserId(userId)) {
            throw new ConflictException("Cannot delete user with existing bank accounts");
        }
        userRepository.deleteById(userId);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .address(AddressMapper.toDto(user.getAddress()))
                .createdTimestamp(user.getCreatedTimestamp())
                .updatedTimestamp(user.getUpdatedTimestamp())
                .build();
    }
}
