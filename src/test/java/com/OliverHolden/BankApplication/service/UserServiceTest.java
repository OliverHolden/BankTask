package com.OliverHolden.BankApplication.service;

import com.OliverHolden.BankApplication.dto.AddressDto;
import com.OliverHolden.BankApplication.dto.request.CreateUserRequest;
import com.OliverHolden.BankApplication.dto.request.UpdateUserRequest;
import com.OliverHolden.BankApplication.dto.response.UserResponse;
import com.OliverHolden.BankApplication.exception.ConflictException;
import com.OliverHolden.BankApplication.exception.ForbiddenException;
import com.OliverHolden.BankApplication.exception.NotFoundException;
import com.OliverHolden.BankApplication.model.Address;
import com.OliverHolden.BankApplication.model.User;
import com.OliverHolden.BankApplication.repository.AccountRepository;
import com.OliverHolden.BankApplication.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks UserService userService;

    User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id("usr-existing")
                .name("Existing User")
                .email("existing@example.com")
                .passwordHash("hashed")
                .phoneNumber("+447911000000")
                .address(Address.builder()
                        .line1("1 Existing St")
                        .town("London")
                        .county("Greater London")
                        .postcode("SW1A 1AA")
                        .build())
                .createdTimestamp(OffsetDateTime.now())
                .updatedTimestamp(OffsetDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // Spec: POST /v1/users (operationId: createUser)
    // -------------------------------------------------------------------------

    @Test
    void createUser_validRequest_savesUserAndReturnsResponse() {
        // Spec: 201 — UserResponse must not include password; id must match ^usr-[A-Za-z0-9]+$
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createUser(buildCreateRequest("new@example.com"));

        assertThat(response.getId()).matches("^usr-[A-Za-z0-9]+$");
        assertThat(response.getName()).isEqualTo("New User");
        assertThat(response.getEmail()).isEqualTo("new@example.com");
        assertThat(response.getAddress()).isNotNull();
        assertThat(response.getCreatedTimestamp()).isNotNull();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateEmail_throwsConflictException() {
        // Spec: 409 — email already registered (pre-check path)
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(buildCreateRequest("existing@example.com")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_concurrentDuplicateEmail_throwsConflictException() {
        // Two concurrent requests can both pass the existsByEmail pre-check; the second hits
        // the DB unique constraint and throws DataIntegrityViolationException — must surface as 409
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));

        assertThatThrownBy(() -> userService.createUser(buildCreateRequest("new@example.com")))
                .isInstanceOf(ConflictException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: GET /v1/users/{userId} (operationId: fetchUserByID)
    // -------------------------------------------------------------------------

    @Test
    void getUser_ownRecord_returnsUserResponse() {
        // Spec: 200 — returns UserResponse for the authenticated user's own record
        when(userRepository.findById("usr-existing")).thenReturn(Optional.of(existingUser));

        UserResponse response = userService.getUser("usr-existing", "usr-existing");

        assertThat(response.getId()).isEqualTo("usr-existing");
        assertThat(response.getEmail()).isEqualTo("existing@example.com");
    }

    @Test
    void getUser_differentUser_throwsForbiddenException() {
        // Spec: 403 — authenticated user may not access another user's record
        assertThatThrownBy(() -> userService.getUser("usr-other", "usr-existing"))
                .isInstanceOf(ForbiddenException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void getUser_notFound_throwsNotFoundException() {
        // Spec: 404 — user does not exist
        when(userRepository.findById("usr-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("usr-missing", "usr-missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: PATCH /v1/users/{userId} (operationId: updateUserByID)
    // PATCH is a partial update — omitted fields must remain unchanged
    // -------------------------------------------------------------------------

    @Test
    void updateUser_partialUpdate_updatesOnlySuppliedFields() {
        // Spec: PATCH — only fields present in the payload are modified
        UpdateUserRequest request = new UpdateUserRequest();
        request.setName("Updated Name");

        when(userRepository.findById("usr-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.updateUser("usr-existing", request, "usr-existing");

        assertThat(response.getName()).isEqualTo("Updated Name");
        assertThat(response.getEmail()).isEqualTo("existing@example.com"); // unchanged
        assertThat(response.getPhoneNumber()).isEqualTo("+447911000000");  // unchanged
    }

    @Test
    void updateUser_emailChange_checksUniquenessBeforeSaving() {
        // Spec: changing email to a new address must verify it is not already taken
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("new@example.com");

        when(userRepository.findById("usr-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.updateUser("usr-existing", request, "usr-existing");

        assertThat(response.getEmail()).isEqualTo("new@example.com");
        verify(userRepository).existsByEmail("new@example.com");
    }

    @Test
    void updateUser_emailChangeToDuplicate_throwsConflictException() {
        // Spec: 409 — new email is already registered to another user
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("taken@example.com");

        when(userRepository.findById("usr-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser("usr-existing", request, "usr-existing"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_emailUnchanged_skipsUniquenessCheck() {
        // Supplying the same email must not trigger the duplicate check
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("existing@example.com");

        when(userRepository.findById("usr-existing")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser("usr-existing", request, "usr-existing");

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateUser_differentUser_throwsForbiddenException() {
        // Spec: 403 — authenticated user may not update another user's record
        assertThatThrownBy(() -> userService.updateUser("usr-other", new UpdateUserRequest(), "usr-existing"))
                .isInstanceOf(ForbiddenException.class);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateUser_notFound_throwsNotFoundException() {
        // Spec: 404 — user does not exist
        when(userRepository.findById("usr-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser("usr-missing", new UpdateUserRequest(), "usr-missing"))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Spec: DELETE /v1/users/{userId} (operationId: deleteUserByID)
    // -------------------------------------------------------------------------

    @Test
    void deleteUser_noAccounts_deletesUser() {
        // Spec: 204 — user with no accounts is deleted
        when(userRepository.existsById("usr-existing")).thenReturn(true);
        when(accountRepository.existsByUserId("usr-existing")).thenReturn(false);

        userService.deleteUser("usr-existing", "usr-existing");

        verify(userRepository).deleteById("usr-existing");
    }

    @Test
    void deleteUser_hasAccounts_throwsConflictException() {
        // Spec: 409 — a user cannot be deleted when associated with a bank account
        when(userRepository.existsById("usr-existing")).thenReturn(true);
        when(accountRepository.existsByUserId("usr-existing")).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteUser("usr-existing", "usr-existing"))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_differentUser_throwsForbiddenException() {
        // Spec: 403 — authenticated user may not delete another user's account
        assertThatThrownBy(() -> userService.deleteUser("usr-other", "usr-existing"))
                .isInstanceOf(ForbiddenException.class);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void deleteUser_notFound_throwsNotFoundException() {
        // Spec: 404 — user does not exist
        when(userRepository.existsById("usr-missing")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser("usr-missing", "usr-missing"))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).deleteById(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CreateUserRequest buildCreateRequest(String email) {
        AddressDto address = AddressDto.builder()
                .line1("1 Test St")
                .town("London")
                .county("Greater London")
                .postcode("SW1A 1AA")
                .build();
        CreateUserRequest request = new CreateUserRequest();
        request.setName("New User");
        request.setEmail(email);
        request.setPassword("Password123!");
        request.setPhoneNumber("+447911123456");
        request.setAddress(address);
        return request;
    }
}
