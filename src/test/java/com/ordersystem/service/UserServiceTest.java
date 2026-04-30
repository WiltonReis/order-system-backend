package com.ordersystem.service;

import com.ordersystem.dto.request.UserRequest;
import com.ordersystem.dto.request.UserUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.UserResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerSaasRepository customerSaasRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User buildUser(UUID id, String email, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    // --- create ---

    @Test
    void shouldCreateUserSuccessfully() {
        UUID id = UUID.randomUUID();
        UserRequest request = new UserRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("secret");
        request.setRole(Role.USER);

        when(userRepository.existsByEmailGlobal("alice@test.local")).thenReturn(false);
        when(customerSaasRepository.getReferenceById(TENANT_ID)).thenReturn(new CustomerSaas());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenReturn(buildUser(id, "alice@test.local", "Alice", Role.USER));

        UserResponse response = userService.create(request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getEmail()).isEqualTo("alice@test.local");
        assertThat(response.getName()).isEqualTo("Alice");
        assertThat(response.getRole()).isEqualTo(Role.USER);
        verify(passwordEncoder).encode("secret");
    }

    @Test
    void shouldThrowWhenCreatingUserWithAlreadyTakenEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("secret");
        request.setRole(Role.USER);
        when(userRepository.existsByEmailGlobal("alice@test.local")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alice@test.local");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldEncodePasswordOnCreate() {
        UserRequest request = new UserRequest();
        request.setEmail("bob@test.local");
        request.setName("Bob");
        request.setPassword("plaintext");
        request.setRole(Role.ADMIN);

        when(userRepository.existsByEmailGlobal("bob@test.local")).thenReturn(false);
        when(customerSaasRepository.getReferenceById(TENANT_ID)).thenReturn(new CustomerSaas());
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request);

        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPassword())));
    }

    // --- findAll ---

    @Test
    void shouldReturnPagedUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        UUID id = UUID.randomUUID();
        Page<User> page = new PageImpl<>(List.of(buildUser(id, "bob@test.local", "Bob", Role.USER)), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);

        Page<UserResponse> result = userService.findAll(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("bob@test.local");
        assertThat(result.getContent().get(0).getName()).isEqualTo("Bob");
    }

    @Test
    void shouldReturnEmptyPageWhenNoUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<UserResponse> result = userService.findAll(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // --- update ---

    @Test
    void shouldUpdateUserWithoutChangingPasswordWhenPasswordIsBlank() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = userService.update(id, request);

        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldUpdateUserWithoutChangingPasswordWhenPasswordIsNull() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword(null);
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(id, request);

        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldEncodeAndUpdatePasswordWhenNewPasswordProvided() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("newpass");
        request.setRole(Role.USER);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded-newpass");
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(id, request);

        verify(passwordEncoder).encode("newpass");
        assertThat(existing.getPassword()).isEqualTo("encoded-newpass");
    }

    @Test
    void shouldThrowWhenUpdatingToAlreadyTakenEmail() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("bob@test.local");
        request.setName("Alice");
        request.setRole(Role.USER);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmailGlobal("bob@test.local")).thenReturn(true);

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bob@test.local");
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotCheckEmailAvailabilityWhenEmailUnchanged() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice Updated");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.update(id, request);

        verify(userRepository, never()).existsByEmailGlobal(any());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentUser() {
        UUID id = UUID.randomUUID();
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("ghost@test.local");
        request.setName("Ghost");
        request.setRole(Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenUpdatingAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("master@test.local");
        request.setName("Master");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    // --- updateRole ---

    @Test
    void shouldUpdateUserRoleToAdmin() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = userService.updateRole(id, Role.ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldUpdateUserRoleToUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "admin@test.local", "Admin", Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = userService.updateRole(id, Role.USER);

        assertThat(response.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldThrowWhenUpdatingRoleOfNonExistentUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(id, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldThrowWhenUpdatingRoleOfAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.updateRole(id, Role.USER))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void shouldDeleteUserSuccessfully() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "alice@test.local", "Alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        MessageResponse response = userService.delete(id);

        assertThat(response.getMessage()).isEqualTo("User deleted successfully");
        verify(userRepository).delete(user);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void shouldThrowWhenDeletingAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User master = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        when(userRepository.findById(id)).thenReturn(Optional.of(master));

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(BusinessException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    // --- cenário futuro / SEC-05 ---

    @Test
    void shouldPersistNewRoleOnUpdateRole() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateRole(id, Role.ADMIN);

        verify(userRepository).save(argThat(u -> Role.ADMIN.equals(u.getRole())));
    }
}
