package com.ordersystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersystem.config.CookieProperties;
import com.ordersystem.config.CorsProperties;
import com.ordersystem.config.JwtProperties;
import com.ordersystem.config.SecurityConfig;
import com.ordersystem.dto.response.AuthResponse;
import com.ordersystem.dto.response.RegisterResponse;
import com.ordersystem.security.JwtAuthenticationFilter;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserDetailsServiceImpl;
import com.ordersystem.service.AuthService;
import com.ordersystem.service.LoginRateLimiterService;
import com.ordersystem.service.RefreshTokenService;
import com.ordersystem.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, CookieProperties.class})
@TestPropertySource(properties = "jwt.secret=dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3NlczkxMjM=")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginRateLimiterService loginRateLimiterService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void login_validCredentials_returns200WithCookie() throws Exception {
        when(loginRateLimiterService.isAllowed(anyString())).thenReturn(true);
        AuthResponse authResponse = new AuthResponse(UUID.randomUUID(), "token", "Bearer",
                "user@test.com", "Test User", "USER", UUID.randomUUID());
        when(authService.login(any())).thenReturn(new AuthService.LoginResult(authResponse, "refresh-token"));
        when(jwtTokenProvider.generateToken(any())).thenReturn("new-token");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("oms.token=")));
    }

    @Test
    void login_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "notanemail",
                                "password", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(loginRateLimiterService.isAllowed(anyString())).thenReturn(true);
        when(authService.login(any())).thenThrow(new BadCredentialsException("Senha incorreta"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "user@test.com",
                                "password", "wrongpassword"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_validBody_returns201() throws Exception {
        when(loginRateLimiterService.isAllowed(anyString())).thenReturn(true);
        RegisterResponse response = new RegisterResponse(UUID.randomUUID(), "user@test.com",
                "Test User", UUID.randomUUID());
        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyName", "ACME Corp",
                                "cpfCnpj", "11222333000181",
                                "name", "Test User",
                                "email", "user@test.com",
                                "password", "Password1"))))
                .andExpect(status().isCreated());
    }

    @Test
    void register_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_returns200() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk());
    }
}
