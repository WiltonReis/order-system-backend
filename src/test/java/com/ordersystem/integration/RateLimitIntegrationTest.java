package com.ordersystem.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Rate limit — esgotamento de bucket no /auth/login")
class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String LOGIN_EMAIL = "ratelimit@ratelimit.test";
    private static final String LOGIN_PASSWORD = "Rate1234";

    @BeforeAll
    void setup() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", "Rate Limit Corp Ltda",
                "cpfCnpj", "60701190000104",
                "name", "Admin Rate",
                "email", LOGIN_EMAIL,
                "password", LOGIN_PASSWORD
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-Forwarded-For", "10.0.0.99"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Sexta tentativa de login retorna 429 após esgotar 5 tokens")
    void sixthLoginAttemptReturns429() throws Exception {
        String wrongPasswordBody = objectMapper.writeValueAsString(Map.of(
                "email", LOGIN_EMAIL,
                "password", "WrongPass1"
        ));
        String ip = "10.1.1.1";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody)
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody)
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Chamadas subsequentes após bloqueio também retornam 429")
    void subsequentCallsAfterBlockAlsoReturn429() throws Exception {
        String wrongPasswordBody = objectMapper.writeValueAsString(Map.of(
                "email", LOGIN_EMAIL,
                "password", "WrongPass2"
        ));
        String ip = "10.2.2.2";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody)
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isUnauthorized());
        }

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(wrongPasswordBody)
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isTooManyRequests());
        }
    }
}
