package com.ordersystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.Cookie;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Performance de queries — ausência de N+1 em listagem de pedidos")
class OrderQueryPerformanceTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private String adminCookie;
    private final List<UUID> productIds = new ArrayList<>();

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @BeforeAll
    void setup() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", "Perf Test Corp Ltda",
                "cpfCnpj", "11444777000161",
                "name", "Admin Perf",
                "email", "admin-perf@perftest.test",
                "password", "Admin1234"
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        adminCookie = login("admin-perf@perftest.test", "Admin1234");

        for (int i = 1; i <= 3; i++) {
            productIds.add(createProduct("Produto " + i, new BigDecimal(10 * i)));
        }

        for (int i = 0; i < 10; i++) {
            createOrderWithItems(List.of(
                    Map.of("productId", productIds.get(0).toString(), "quantity", 2),
                    Map.of("productId", productIds.get(1).toString(), "quantity", 1),
                    Map.of("productId", productIds.get(2).toString(), "quantity", 3)
            ));
        }
    }

    @Test
    @DisplayName("GET /orders/details com 10 pedidos (3 itens cada) executa no máximo 6 queries SQL")
    void findAllDetailsExecutesFixedQueryCount() throws Exception {
        Statistics stats = stats();
        stats.clear();

        mockMvc.perform(get("/orders/details")
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());

        long sqlCount = stats.getPrepareStatementCount();
        assertThat(sqlCount)
                .as("N+1 detectado: %d queries para 10 pedidos (esperado ≤ 6)", sqlCount)
                .isLessThanOrEqualTo(6L);
    }

    @Test
    @DisplayName("Número de queries é o mesmo na página 1 e na página 2")
    void queryCountIsConstantAcrossPages() throws Exception {
        Statistics stats = stats();

        stats.clear();
        mockMvc.perform(get("/orders/details?page=0&size=5")
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());
        long countPage1 = stats.getPrepareStatementCount();

        stats.clear();
        mockMvc.perform(get("/orders/details?page=1&size=5")
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());
        long countPage2 = stats.getPrepareStatementCount();

        assertThat(countPage1).isLessThanOrEqualTo(6L);
        assertThat(countPage2).isLessThanOrEqualTo(6L);
        assertThat(countPage1).isEqualTo(countPage2);
    }

    // --- helpers ---

    private Statistics stats() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
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
                .andReturn().getResponse().getCookie("oms.token");

        assert tokenCookie != null;
        return tokenCookie.getValue();
    }

    private UUID createProduct(String name, BigDecimal price) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name, "price", price));
        String response = mockMvc.perform(post("/products")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }

    private void createOrderWithItems(List<Map<String, Object>> items) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("items", items));
        mockMvc.perform(post("/orders/full")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
