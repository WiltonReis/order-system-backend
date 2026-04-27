package com.ordersystem.service;

import com.ordersystem.dto.request.LoginRequest;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.entity.User;
import com.ordersystem.enums.Role;
import com.ordersystem.exception.ResourceNotFoundException;
import com.ordersystem.repository.UserRepository;
import com.ordersystem.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
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
    private UserRepository userRepository;

    @InjectMocks
    private AuthService authService;

    private LoginRequest buildLoginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private User buildUser(UUID id, String username, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("encoded");
        user.setRole(role);
        return user;
    }

    private Authentication mockAuthentication(String username) {
        UserDetails userDetails = mock(UserDetails.class);
        //when(userDetails.getUsername()).thenReturn(username);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        return auth;
    }

    @Test
    void shouldReturnAuthResponseWhenLoginSucceeds() {
        // Given
        UUID userId = UUID.randomUUID();
        LoginRequest request = buildLoginRequest("john", "password123");
        Authentication auth = mockAuthentication("john");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any(UserDetails.class))).thenReturn("jwt-token");
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(buildUser(userId, "john", Role.USER)));

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getType()).isEqualTo("Bearer");
        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void shouldReturnAdminRoleWhenAdminLogsIn() {
        // Given
        LoginRequest request = buildLoginRequest("admin", "pass");
        Authentication auth = mockAuthentication("admin");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("admin-token");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(buildUser(UUID.randomUUID(), "admin", Role.ADMIN)));

        // When
        AuthResponse response = authService.login(request);

        // Then
        assertThat(response.getRole()).isEqualTo("ADMIN");
        assertThat(response.getToken()).isEqualTo("admin-token");
    }

    @Test
    void shouldThrowBadCredentialsWhenAuthenticationFails() {
        // Given
        LoginRequest request = buildLoginRequest("john", "wrong");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        // When / Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(userRepository, jwtTokenProvider);
    }

    @Test
    void shouldThrowResourceNotFoundWhenUserDisappearsAfterAuthentication() {
        // Given — QUAL-03: segunda query ao banco pode falhar em caso de deleção concorrente
        LoginRequest request = buildLoginRequest("ghost", "password");
        Authentication auth = mockAuthentication("ghost");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("token");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shouldCallAuthenticationManagerWithCorrectCredentials() {
        // Given
        LoginRequest request = buildLoginRequest("alice", "secret");
        Authentication auth = mockAuthentication("alice");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("token");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(buildUser(UUID.randomUUID(), "alice", Role.USER)));

        // When
        authService.login(request);

        // Then
        verify(authenticationManager).authenticate(argThat(token ->
                token instanceof UsernamePasswordAuthenticationToken ut
                        && "alice".equals(ut.getPrincipal())
                        && "secret".equals(ut.getCredentials())
        ));
    }

    @Test
    void shouldGenerateTokenForAuthenticatedUserDetails() {
        // Given
        LoginRequest request = buildLoginRequest("bob", "pass");
        UserDetails userDetails = mock(UserDetails.class);
        //when(userDetails.getUsername()).thenReturn("bob");
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("bob-token");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(buildUser(UUID.randomUUID(), "bob", Role.USER)));

        // When
        AuthResponse response = authService.login(request);

        // Then
        verify(jwtTokenProvider).generateToken(userDetails);
        assertThat(response.getToken()).isEqualTo("bob-token");
    }
}
