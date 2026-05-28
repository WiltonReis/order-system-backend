package com.ordersystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Autorização por role — USER recebe 403, ADMIN obtém sucesso")
class AuthorizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminCookie;
    private String userCookie;
    private UUID sharedProductId;
    private UUID sharedOrderId;
    private UUID sharedUserId;

    @BeforeAll
    void setup() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", "Auth Corp Ltda",
                "cpfCnpj", "60746948000112",
                "name", "Admin Auth",
                "email", "admin-auth@authtest.test",
                "password", "Admin1234"
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        adminCookie = login("admin-auth@authtest.test", "Admin1234");

        sharedUserId = createUser("user-auth@authtest.test", "User Auth", "User1234", "USER");
        userCookie = login("user-auth@authtest.test", "User1234");

        sharedProductId = createProduct(adminCookie, "Produto Auth", new BigDecimal("100.00"));

        sharedOrderId = createOrder(adminCookie, sharedProductId);
    }

    @Nested
    @DisplayName("USER recebe 403 em endpoints restritos")
    class UserForbidden {

        @Test
        @DisplayName("USER não pode aplicar desconto em pedido")
        void userCannotApplyDiscount() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("discount", "10.00"));
            mockMvc.perform(put("/orders/{id}", sharedOrderId)
                            .cookie(new Cookie("oms.token", userCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode deletar pedido")
        void userCannotDeleteOrder() throws Exception {
            mockMvc.perform(delete("/orders/{id}", sharedOrderId)
                            .cookie(new Cookie("oms.token", userCookie)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode restaurar pedido")
        void userCannotRestoreOrder() throws Exception {
            mockMvc.perform(post("/orders/{id}/restore", sharedOrderId)
                            .cookie(new Cookie("oms.token", userCookie)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode criar produto")
        void userCannotCreateProduct() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "name", "Produto Proibido",
                    "price", "50.00"
            ));
            mockMvc.perform(post("/products")
                            .cookie(new Cookie("oms.token", userCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode deletar produto")
        void userCannotDeleteProduct() throws Exception {
            mockMvc.perform(delete("/products/{id}", sharedProductId)
                            .cookie(new Cookie("oms.token", userCookie)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode criar outro usuário")
        void userCannotCreateUser() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "email", "newuser@authtest.test",
                    "name", "New User",
                    "password", "Pass1234",
                    "role", "USER"
            ));
            mockMvc.perform(post("/users")
                            .cookie(new Cookie("oms.token", userCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("USER não pode deletar usuário")
        void userCannotDeleteUser() throws Exception {
            mockMvc.perform(delete("/users/{id}", sharedUserId)
                            .cookie(new Cookie("oms.token", userCookie)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("ADMIN obtém sucesso nos mesmos endpoints")
    class AdminSuccess {

        @Test
        @DisplayName("ADMIN pode criar produto")
        void adminCanCreateProduct() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "name", "Produto Admin",
                    "price", "200.00"
            ));
            mockMvc.perform(post("/products")
                            .cookie(new Cookie("oms.token", adminCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ADMIN pode aplicar desconto em pedido")
        void adminCanApplyDiscount() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("discount", "5.00"));
            mockMvc.perform(put("/orders/{id}", sharedOrderId)
                            .cookie(new Cookie("oms.token", adminCookie))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN pode deletar e restaurar pedido")
        void adminCanDeleteAndRestoreOrder() throws Exception {
            UUID orderId = createOrder(adminCookie, sharedProductId);

            mockMvc.perform(delete("/orders/{id}", orderId)
                            .cookie(new Cookie("oms.token", adminCookie)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/orders/{id}/restore", orderId)
                            .cookie(new Cookie("oms.token", adminCookie)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN pode criar e deletar usuário")
        void adminCanCreateAndDeleteUser() throws Exception {
            UUID userId = createUser("temp-admin@authtest.test", "Temp Admin", "Temp1234", "USER");

            mockMvc.perform(delete("/users/{id}", userId)
                            .cookie(new Cookie("oms.token", adminCookie)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN pode deletar produto")
        void adminCanDeleteProduct() throws Exception {
            UUID productId = createProduct(adminCookie, "Produto Para Deletar", new BigDecimal("10.00"));

            mockMvc.perform(delete("/products/{id}", productId)
                            .cookie(new Cookie("oms.token", adminCookie)))
                    .andExpect(status().isOk());
        }
    }

    // --- helpers ---

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password
        ));
        Cookie tokenCookie = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("oms.token");

        assert tokenCookie != null;
        return tokenCookie.getValue();
    }

    private UUID createUser(String email, String name, String password, String role) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "name", name,
                "password", password,
                "role", role
        ));
        String response = mockMvc.perform(post("/users")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private UUID createProduct(String cookie, String name, BigDecimal price) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "price", price
        ));
        String response = mockMvc.perform(post("/products")
                        .cookie(new Cookie("oms.token", cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private UUID createOrder(String cookie, UUID productId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId.toString(), "quantity", 1))
        ));
        String response = mockMvc.perform(post("/orders/full")
                        .cookie(new Cookie("oms.token", cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }
}
