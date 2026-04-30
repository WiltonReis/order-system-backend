package com.ordersystem.service;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.enums.Role;
import com.ordersystem.repository.CustomerSaasRepository;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CustomerSaasRepository customerSaasRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private UserPrincipal buildPrincipal(UUID id, String email, String name, Role role) {
        return new UserPrincipal(
                id, email, name, UUID.randomUUID(), "encoded",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private Authentication mockAuthentication(UserPrincipal principal) {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    @Test
    void shouldReturnLoginResultWhenLoginSucceeds() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("john@test.local", "password123");
        UserPrincipal principal = buildPrincipal(userId, "john@test.local", "John", Role.USER);
        Authentication auth = mockAuthentication(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtTokenProvider.generateToken(principal)).thenReturn("jwt-token");
        when(refreshTokenService.create(userId)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);
        AuthResponse response = result.authResponse();

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("john@test.local");
        assertThat(response.getName()).isEqualTo("John");
        assertThat(response.getRole()).isEqualTo("USER");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void shouldReturnAdminRoleWhenAdminLogsIn() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("admin@test.local", "pass");
        UserPrincipal principal = buildPrincipal(userId, "admin@test.local", "Admin", Role.ADMIN);
        Authentication auth = mockAuthentication(principal);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(principal)).thenReturn("admin-token");
        when(refreshTokenService.create(userId)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);

        assertThat(result.authResponse().getRole()).isEqualTo("ADMIN");
        assertThat(result.authResponse().getToken()).isEqualTo("admin-token");
    }

    @Test
    void shouldThrowBadCredentialsWhenAuthenticationFails() {
        LoginRequest request = buildLoginRequest("john@test.local", "wrong");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void shouldCallAuthenticationManagerWithCorrectCredentials() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("alice@test.local", "secret");
        UserPrincipal principal = buildPrincipal(userId, "alice@test.local", "Alice", Role.USER);
        Authentication auth = mockAuthentication(principal);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("token");
        when(refreshTokenService.create(any(UUID.class))).thenReturn("refresh-token");

        authService.login(request);

        verify(authenticationManager).authenticate(argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken ut
                        && "alice@test.local".equals(ut.getPrincipal())
                        && "secret".equals(ut.getCredentials())
        ));
    }

    @Test
    void shouldGenerateTokenForAuthenticatedPrincipal() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("bob@test.local", "pass");
        UserPrincipal principal = buildPrincipal(userId, "bob@test.local", "Bob", Role.USER);
        Authentication auth = mockAuthentication(principal);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(principal)).thenReturn("bob-token");
        when(refreshTokenService.create(userId)).thenReturn("refresh-token");

        AuthService.LoginResult result = authService.login(request);

        verify(jwtTokenProvider).generateToken(principal);
        assertThat(result.authResponse().getToken()).isEqualTo("bob-token");
    }

    @Test
    void shouldCreateRefreshTokenWithPrincipalId() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("bob@test.local", "pass");
        UserPrincipal principal = buildPrincipal(userId, "bob@test.local", "Bob", Role.USER);
        Authentication auth = mockAuthentication(principal);

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("token");
        when(refreshTokenService.create(userId)).thenReturn("rt-value");

        AuthService.LoginResult result = authService.login(request);

        verify(refreshTokenService).create(userId);
        assertThat(result.refreshToken()).isEqualTo("rt-value");
    }
}
