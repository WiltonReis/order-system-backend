package com.ordersystem.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserDetailsServiceImpl;

@DisplayName("Segurança de token — expirado, adulterado e revogado")
class TokenSecurityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    private static final String ADMIN_EMAIL = "admin-token@tokentest.test";
    private static final String ADMIN_PASSWORD = "Admin1234";

    @BeforeAll
    void setup() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", "Token Sec Ltda",
                "cpfCnpj", "33000167000101",
                "name", "Admin Token",
                "email", ADMIN_EMAIL,
                "password", ADMIN_PASSWORD
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Token expirado é rejeitado com 401")
    void expiredTokenIsRejected() throws Exception {
        UserDetails userDetails = userDetailsService.loadUserByUsername(ADMIN_EMAIL);
        String expiredToken = jwtTokenProvider.generateToken(userDetails, -1000L);

        mockMvc.perform(get("/orders")
                        .cookie(new Cookie("oms.token", expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token com assinatura adulterada é rejeitado com 401")
    void tamperedSignatureIsRejected() throws Exception {
        UserDetails userDetails = userDetailsService.loadUserByUsername(ADMIN_EMAIL);
        String validToken = jwtTokenProvider.generateToken(userDetails);

        String tampered = validToken.substring(0, validToken.length() - 4) + "XXXX";

        mockMvc.perform(get("/orders")
                        .cookie(new Cookie("oms.token", tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token de acesso revogado após logout é rejeitado com 401")
    void revokedAccessTokenIsRejected() throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", ADMIN_EMAIL,
                "password", ADMIN_PASSWORD
        ));
        Cookie tokenCookie = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("oms.token");

        assert tokenCookie != null;
        String accessToken = tokenCookie.getValue();

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("oms.token", accessToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders")
                        .cookie(new Cookie("oms.token", accessToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh token revogado após logout é rejeitado com 401")
    void revokedRefreshTokenIsRejected() throws Exception {
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", ADMIN_EMAIL,
                "password", ADMIN_PASSWORD
        ));
        var response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Cookie accessCookie = response.getCookie("oms.token");
        Cookie refreshCookie = response.getCookie("oms.refresh");

        assert accessCookie != null;
        assert refreshCookie != null;

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("oms.token", accessCookie.getValue()),
                                new Cookie("oms.refresh", refreshCookie.getValue())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("oms.refresh", refreshCookie.getValue())))
                .andExpect(status().isUnauthorized());
    }
}
