package com.ordersystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ordersystem.config.CookieProperties;
import com.ordersystem.config.CorsProperties;
import com.ordersystem.config.JwtProperties;
import com.ordersystem.config.SecurityConfig;
import com.ordersystem.dto.response.MessageResponse;
import com.ordersystem.dto.response.OrderDetailResponse;
import com.ordersystem.dto.response.OrderResponse;
import com.ordersystem.dto.response.OrderUpdateResponse;
import com.ordersystem.exception.BusinessException;
import com.ordersystem.security.JwtAuthenticationFilter;
import com.ordersystem.security.JwtTokenProvider;
import com.ordersystem.security.UserDetailsServiceImpl;
import com.ordersystem.service.OrderService;
import com.ordersystem.service.TokenBlacklistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@EnableConfigurationProperties({CorsProperties.class, JwtProperties.class, CookieProperties.class})
@TestPropertySource(properties = "jwt.secret=dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3NlczkxMjM=")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsServiceImpl userDetailsService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser
    @DisplayName("POST /orders autenticado cria pedido vazio e retorna 201")
    void create_authenticated_returns201() throws Exception {
        when(orderService.create(any())).thenReturn(mock(OrderResponse.class));

        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders/full com lista de itens vazia retorna 400 por falha de validação")
    void createFull_emptyItems_returns400() throws Exception {
        mockMvc.perform(post("/orders/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerName", "Test Customer",
                                "items", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /orders/full com itens válidos cria o pedido completo e retorna 201")
    void createFull_validBody_returns201() throws Exception {
        when(orderService.createFull(any())).thenReturn(mock(OrderDetailResponse.class));

        mockMvc.perform(post("/orders/full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerName", "Test Customer",
                                "items", List.of(Map.of(
                                        "productId", UUID.randomUUID().toString(),
                                        "quantity", 1))))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("PUT /orders/{id} (aplicar desconto) como USER comum é negado com 403")
    void applyDiscount_asUser_returns403() throws Exception {
        mockMvc.perform(put("/orders/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("discount", "0.00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /orders/{id} (aplicar desconto) como ADMIN retorna 200")
    void applyDiscount_asAdmin_returns200() throws Exception {
        when(orderService.applyDiscount(any(), any())).thenReturn(mock(OrderUpdateResponse.class));

        mockMvc.perform(put("/orders/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("discount", "0.00"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("PUT /orders/{id} mapeia BusinessException para 422")
    void applyDiscount_businessException_returns422() throws Exception {
        when(orderService.applyDiscount(any(), any()))
                .thenThrow(new BusinessException("Desconto excede o subtotal do pedido"));

        mockMvc.perform(put("/orders/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("discount", "9999.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /orders/{id}/pdf retorna 200 com Content-Type application/pdf")
    void exportPdf_authenticated_returnsApplicationPdf() throws Exception {
        byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46};
        when(orderService.exportPdf(any())).thenReturn(pdfBytes);

        mockMvc.perform(get("/orders/{id}/pdf", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @DisplayName("GET /orders sem autenticação é bloqueado com erro 4xx")
    void findAll_anonymous_returns4xx() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /orders/{id} como ADMIN retorna 200")
    void delete_asAdmin_returns200() throws Exception {
        when(orderService.delete(any())).thenReturn(new MessageResponse("Pedido excluído com sucesso"));

        mockMvc.perform(delete("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("DELETE /orders/{id} como USER comum é negado com 403")
    void delete_asUser_returns403() throws Exception {
        mockMvc.perform(delete("/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
