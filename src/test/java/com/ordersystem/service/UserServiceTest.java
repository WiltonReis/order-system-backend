package com.ordersystem.service;

import com.ordersystem.dto.request.UserRequest;
import com.ordersystem.dto.request.UserUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.UserResponse;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.UserRepository;
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

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User buildUser(UUID id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    // --- create ---

    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        UserRequest request = new UserRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setRole(Role.USER);

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenReturn(buildUser(id, "alice", Role.USER));

        // When
        UserResponse response = userService.create(request);

        // Then
        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo(Role.USER);
        verify(passwordEncoder).encode("secret");
    }

    @Test
    void shouldThrowWhenCreatingUserWithAlreadyTakenUsername() {
        // Given
        UserRequest request = new UserRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setRole(Role.USER);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("alice");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldEncodePasswordOnCreate() {
        // Given
        UserRequest request = new UserRequest();
        request.setUsername("bob");
        request.setPassword("plaintext");
        request.setRole(Role.ADMIN);
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        userService.create(request);

        // Then
        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPassword())));
    }

    // --- findAll ---

    @Test
    void shouldReturnPagedUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        UUID id = UUID.randomUUID();
        Page<User> page = new PageImpl<>(List.of(buildUser(id, "bob", Role.USER)), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<UserResponse> result = userService.findAll(pageable);

        // Then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getUsername()).isEqualTo("bob");
    }

    @Test
    void shouldReturnEmptyPageWhenNoUsers() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        // When
        Page<UserResponse> result = userService.findAll(pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // --- update ---

    @Test
    void shouldUpdateUserWithoutChangingPasswordWhenPasswordIsBlank() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("alice");
        request.setPassword("");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        UserResponse response = userService.update(id, request);

        // Then
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldUpdateUserWithoutChangingPasswordWhenPasswordIsNull() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("alice");
        request.setPassword(null);
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        userService.update(id, request);

        // Then
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void shouldEncodeAndUpdatePasswordWhenNewPasswordProvided() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("alice");
        request.setPassword("newpass");
        request.setRole(Role.USER);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded-newpass");
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        userService.update(id, request);

        // Then
        verify(passwordEncoder).encode("newpass");
        assertThat(existing.getPassword()).isEqualTo("encoded-newpass");
    }

    @Test
    void shouldThrowWhenUpdatingToAlreadyTakenUsername() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("bob");
        request.setRole(Role.USER);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.existsByUsername("bob")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bob");
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotCheckUsernameAvailabilityWhenUsernameUnchanged() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("alice");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        userService.update(id, request);

        // Then
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentUser() {
        // Given
        UUID id = UUID.randomUUID();
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUsername("ghost");
        request.setRole(Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateRole ---

    @Test
    void shouldUpdateUserRoleToAdmin() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        UserResponse response = userService.updateRole(id, Role.ADMIN);

        // Then
        assertThat(response.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void shouldUpdateUserRoleToUser() {
        // Given
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "admin", Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        UserResponse response = userService.updateRole(id, Role.USER);

        // Then
        assertThat(response.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void shouldThrowWhenUpdatingRoleOfNonExistentUser() {
        // Given
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> userService.updateRole(id, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- delete ---

    @Test
    void shouldDeleteUserSuccessfully() {
        // Given
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(true);

        // When
        MessageResponse response = userService.delete(id);

        // Then
        assertThat(response.getMessage()).isEqualTo("User deleted successfully");
        verify(userRepository).deleteById(id);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentUser() {
        // Given
        UUID id = UUID.randomUUID();
        when(userRepository.existsById(id)).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).deleteById(any());
    }

    // --- Cenários futuros / SEC-05: mudança de role invalida tokens existentes (planejado)

    @Test
    void shouldPersistNewRoleOnUpdateRole() {
        // Given — verifica que a role é de fato salva no banco (pré-condição para SEC-05)
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        // When
        userService.updateRole(id, Role.ADMIN);

        // Then
        verify(userRepository).save(argThat(u -> Role.ADMIN.equals(u.getRole())));
    }
}
