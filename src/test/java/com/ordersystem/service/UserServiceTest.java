package com.ordersystem.service;

import com.ordersystem.dto.request.UserRequest;
import com.ordersystem.dto.request.UserUpdateRequest;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.UserResponse;
import com.ordersystem.entity.CustomerSaas;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.ConflictException;
import com.ordersystem.exception.ForbiddenOperationException;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.mapper.UserMapper;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.TenantContext;
import com.ordersystem.validation.UserValidator;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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

@DisplayName("UserService — criação, consulta, atualização e exclusão de usuários")
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerSaasRepository customerSaasRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Mock
    private UserValidator userValidator;

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
    @DisplayName("cria usuário com sucesso e retorna response")
    void shouldCreateUserSuccessfully() {
        UUID id = UUID.randomUUID();
        UserRequest request = new UserRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("secret");
        request.setRole(Role.USER);

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
    @DisplayName("lança ConflictException ao criar usuário com e-mail já cadastrado")
    void shouldThrowWhenCreatingUserWithAlreadyTakenEmail() {
        UserRequest request = new UserRequest();
        request.setEmail("alice@test.local");
        request.setName("Alice");
        request.setPassword("secret");
        request.setRole(Role.USER);
        doThrow(new ConflictException("E-mail já cadastrado"))
                .when(userValidator).validateEmailNotTaken("alice@test.local");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("codifica a senha antes de persistir")
    void shouldEncodePasswordOnCreate() {
        UserRequest request = new UserRequest();
        request.setEmail("bob@test.local");
        request.setName("Bob");
        request.setPassword("plaintext");
        request.setRole(Role.ADMIN);

        when(customerSaasRepository.getReferenceById(TENANT_ID)).thenReturn(new CustomerSaas());
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.create(request);

        verify(userRepository).save(argThat(u -> "hashed".equals(u.getPassword())));
    }

    // --- findAll ---

    @Test
    @DisplayName("retorna página de usuários")
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
    @DisplayName("retorna página vazia quando não há usuários")
    void shouldReturnEmptyPageWhenNoUsers() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<UserResponse> result = userService.findAll(pageable);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // --- update ---

    @Test
    @DisplayName("atualiza usuário sem alterar senha quando senha está em branco")
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
    @DisplayName("atualiza usuário sem alterar senha quando senha é nula")
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
    @DisplayName("codifica e atualiza senha quando nova senha é fornecida")
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
    @DisplayName("lança ConflictException ao atualizar para e-mail já cadastrado")
    void shouldThrowWhenUpdatingToAlreadyTakenEmail() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("bob@test.local");
        request.setName("Alice");
        request.setRole(Role.USER);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new ConflictException("E-mail já cadastrado"))
                .when(userValidator).validateEmailNotTakenByAnother("alice@test.local", "bob@test.local");

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("não verifica disponibilidade do e-mail quando ele não foi alterado")
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
    @DisplayName("lança ResourceNotFoundException ao atualizar usuário inexistente")
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
    @DisplayName("lança ForbiddenOperationException ao tentar atualizar ADMIN_MASTER")
    void shouldThrowWhenUpdatingAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("master@test.local");
        request.setName("Master");
        request.setRole(Role.ADMIN);

        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new ForbiddenOperationException("Administrador master não pode ser modificado"))
                .when(userValidator).validateNotAdminMaster(Role.ADMIN_MASTER);

        assertThatThrownBy(() -> userService.update(id, request))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userRepository, never()).save(any());
    }

    // --- updateRole ---

    @Test
    @DisplayName("atualiza role do usuário para ADMIN")
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
    @DisplayName("atualiza role do usuário para USER")
    void shouldUpdateUserRoleToUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "admin@test.local", "Admin", Role.ADMIN);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        UserResponse response = userService.updateRole(id, Role.USER);

        assertThat(response.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao atualizar role de usuário inexistente")
    void shouldThrowWhenUpdatingRoleOfNonExistentUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(id, Role.ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("lança ForbiddenOperationException ao tentar alterar role do ADMIN_MASTER")
    void shouldThrowWhenUpdatingRoleOfAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        doThrow(new ForbiddenOperationException("Administrador master não pode ser modificado"))
                .when(userValidator).validateNotAdminMaster(Role.ADMIN_MASTER);

        assertThatThrownBy(() -> userService.updateRole(id, Role.USER))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    @DisplayName("exclui usuário com sucesso e retorna mensagem de confirmação")
    void shouldDeleteUserSuccessfully() {
        UUID id = UUID.randomUUID();
        User user = buildUser(id, "alice@test.local", "Alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        MessageResponse response = userService.delete(id);

        assertThat(response.getMessage()).isEqualTo("Usuário excluído com sucesso");
        verify(userRepository).delete(user);
    }

    @Test
    @DisplayName("lança ResourceNotFoundException ao excluir usuário inexistente")
    void shouldThrowWhenDeletingNonExistentUser() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("lança ForbiddenOperationException ao excluir ADMIN_MASTER")
    void shouldThrowWhenDeletingAdminMasterUser() {
        UUID id = UUID.randomUUID();
        User master = buildUser(id, "master@test.local", "Master", Role.ADMIN_MASTER);
        when(userRepository.findById(id)).thenReturn(Optional.of(master));
        doThrow(new ForbiddenOperationException("Administrador master não pode ser modificado"))
                .when(userValidator).validateNotAdminMaster(Role.ADMIN_MASTER);

        assertThatThrownBy(() -> userService.delete(id))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    @DisplayName("persiste a nova role ao chamar updateRole")
    void shouldPersistNewRoleOnUpdateRole() {
        UUID id = UUID.randomUUID();
        User existing = buildUser(id, "alice@test.local", "Alice", Role.USER);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        userService.updateRole(id, Role.ADMIN);

        verify(userRepository).save(argThat(u -> Role.ADMIN.equals(u.getRole())));
    }
}
