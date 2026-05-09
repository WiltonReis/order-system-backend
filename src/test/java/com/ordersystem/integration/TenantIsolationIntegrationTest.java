package com.ordersystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Isolamento multi-tenant")
//@ActiveProfiles("test")
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String cookieA;
    private String cookieB;
    private UUID productAId;
    private UUID productBId;
    private UUID orderAId;
    private UUID orderBId;

    @BeforeAll
    void setup() throws Exception {
        register("Empresa Alpha Ltda", "11222333000181", "Administrador Alpha", "admin@alpha.test", "Alpha1234");
        cookieA = login("admin@alpha.test", "Alpha1234");
        productAId = createProduct(cookieA, "Produto Alpha", new BigDecimal("50.00"));
        orderAId = createOrder(cookieA, productAId);

        register("Empresa Beta SA", "12345678000195", "Administrador Beta", "admin@beta.test", "Beta1234");
        cookieB = login("admin@beta.test", "Beta1234");
        productBId = createProduct(cookieB, "Produto Beta", new BigDecimal("75.00"));
        orderBId = createOrder(cookieB, productBId);
    }

    // === Pedidos ===

    @Test
    @DisplayName("Tenant A não consegue acessar pedido do Tenant B — espera 404, não 403")
    void tenantA_cannotAccess_tenantB_order() throws Exception {
        mockMvc.perform(get("/orders/{id}", orderBId)
                        .cookie(new Cookie("oms.token", cookieA)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant B acessa seu próprio pedido com sucesso")
    void tenantB_canAccess_ownOrder() throws Exception {
        mockMvc.perform(get("/orders/{id}", orderBId)
                        .cookie(new Cookie("oms.token", cookieB)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tenant A não consegue acessar pedido do Tenant A via cookie B — espera 404")
    void tenantB_cannotAccess_tenantA_order() throws Exception {
        mockMvc.perform(get("/orders/{id}", orderAId)
                        .cookie(new Cookie("oms.token", cookieB)))
                .andExpect(status().isNotFound());
    }

    // === Produtos ===

    @Test
    @DisplayName("Tenant A não consegue atualizar produto do Tenant B — espera 404")
    void tenantA_cannotUpdate_tenantB_product() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Produto adulterado",
                "price", new BigDecimal("1.00")
        ));
        mockMvc.perform(put("/products/{id}", productBId)
                        .cookie(new Cookie("oms.token", cookieA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant B atualiza seu próprio produto com sucesso")
    void tenantB_canUpdate_ownProduct() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Produto Beta Atualizado",
                "price", new BigDecimal("80.00")
        ));
        mockMvc.perform(put("/products/{id}", productBId)
                        .cookie(new Cookie("oms.token", cookieB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // === Usuários ===

    @Test
    @DisplayName("Tenant A não vê usuários do Tenant B na listagem")
    void tenantA_doesNotSee_tenantB_users() throws Exception {
        String response = mockMvc.perform(get("/users")
                        .cookie(new Cookie("oms.token", cookieA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("admin@alpha.test");
        assertThat(response).doesNotContain("admin@beta.test");
    }

    @Test
    @DisplayName("Tenant B não vê usuários do Tenant A na listagem")
    void tenantB_doesNotSee_tenantA_users() throws Exception {
        String response = mockMvc.perform(get("/users")
                        .cookie(new Cookie("oms.token", cookieB)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("admin@beta.test");
        assertThat(response).doesNotContain("admin@alpha.test");
    }

    // === Soft-delete / Restore ===

    @Test
    @DisplayName("Tenant B não consegue restaurar pedido soft-deleted de Tenant A — espera 404")
    void tenantB_cannotRestore_softDeleted_tenantA_order() throws Exception {
        UUID orderToDelete = createOrder(cookieA, productAId);
        mockMvc.perform(delete("/orders/{id}", orderToDelete)
                        .cookie(new Cookie("oms.token", cookieA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/restore", orderToDelete)
                        .cookie(new Cookie("oms.token", cookieB)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tenant A restaura próprio pedido soft-deleted — Tenant B não é afetado")
    void tenantA_restores_ownOrder_tenantB_unaffected() throws Exception {
        UUID orderToRestore = createOrder(cookieA, productAId);
        mockMvc.perform(delete("/orders/{id}", orderToRestore)
                        .cookie(new Cookie("oms.token", cookieA)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/orders/{id}/restore", orderToRestore)
                        .cookie(new Cookie("oms.token", cookieA)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/orders/{id}", orderBId)
                        .cookie(new Cookie("oms.token", cookieB)))
                .andExpect(status().isOk());
    }

    // === Helpers ===

    private void register(String companyName, String cpfCnpj, String name, String email, String password)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", companyName,
                "cpfCnpj", cpfCnpj,
                "name", name,
                "email", email,
                "password", password
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "password", password
        ));
        Cookie tokenCookie = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("oms.token");

        assertThat(tokenCookie).as("Cookie oms.token ausente após login").isNotNull();
        return tokenCookie.getValue();
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
