package com.ordersystem.controller;

import com.ordersystem.config.CookieProperties;
import com.ordersystem.config.CorsProperties;
import com.ordersystem.config.JwtProperties;
import com.ordersystem.config.SecurityConfig;
import com.ordersystem.dto.response.DashboardResponse;
import com.ordersystem.security.JwtAuthenticationFilter;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserDetailsServiceImpl;
import com.ordersystem.service.DashboardService;
import com.ordersystem.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, CookieProperties.class})
@TestPropertySource(properties = "jwt.secret=dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3NlczkxMjM=")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser
    void getDashboard_authenticated_returns200() throws Exception {
        when(dashboardService.getDashboard(anyString(), any(), any()))
                .thenReturn(mock(DashboardResponse.class));

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    void getDashboard_anonymous_returns4xx() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is4xxClientError());
    }
}
