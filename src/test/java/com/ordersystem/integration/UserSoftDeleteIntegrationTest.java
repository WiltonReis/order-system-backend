package com.ordersystem.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Soft-delete de usuário — unicidade e reuso de email/nome")
class UserSoftDeleteIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminCookie;

    @BeforeAll
    void setup() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "companyName", "Empresa Soft Delete Ltda",
                "cpfCnpj", "11144477735",
                "name", "Admin Soft Delete",
                "email", "admin-sd@softdelete.test",
                "password", "Admin1234"
        ));
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        adminCookie = login("admin-sd@softdelete.test", "Admin1234");
    }

    @Test
    @DisplayName("Deve permitir criar usuário com email de usuário soft-deletado")
    void shouldAllowReusingEmailAfterSoftDelete() throws Exception {
        UUID deletedUserId = createUser("alice-reuse@example.test", "Alice Reuse", "Password1");

        mockMvc.perform(delete("/users/{id}", deletedUserId)
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "alice-reuse@example.test",
                                "name", "Alice Nova",
                                "password", "Password1",
                                "role", "USER"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Deve permitir criar usuário com nome de usuário soft-deletado no mesmo tenant")
    void shouldAllowReusingNameAfterSoftDelete() throws Exception {
        UUID deletedUserId = createUser("bob-unique@example.test", "Nome Reutilizavel", "Password1");

        mockMvc.perform(delete("/users/{id}", deletedUserId)
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "bob-novo@example.test",
                                "name", "Nome Reutilizavel",
                                "password", "Password1",
                                "role", "USER"
                        ))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Usuário soft-deletado não aparece na listagem")
    void deletedUserShouldNotAppearInListing() throws Exception {
        UUID deletedUserId = createUser("charlie-hidden@example.test", "Charlie Hidden", "Password1");

        mockMvc.perform(delete("/users/{id}", deletedUserId)
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/users")
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("charlie-hidden@example.test");
    }

    @Test
    @DisplayName("Usuário ativo ainda bloqueia criação de outro com mesmo email")
    void activeUserEmailShouldRemainUnique() throws Exception {
        createUser("dave-active@example.test", "Dave Active", "Password1");

        mockMvc.perform(post("/users")
                        .cookie(new Cookie("oms.token", adminCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "dave-active@example.test",
                                "name", "Dave Duplicado",
                                "password", "Password1",
                                "role", "USER"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Usuário soft-deletado não consegue logar")
    void deletedUserCannotLogin() throws Exception {
        createUser("eve-deleted@example.test", "Eve Deleted", "Password1");
        UUID userId = createUser("eve-login@example.test", "Eve Login", "Password1");

        mockMvc.perform(delete("/users/{id}", userId)
                        .cookie(new Cookie("oms.token", adminCookie)))
                .andExpect(status().isOk());

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "email", "eve-login@example.test",
                "password", "Password1"
        ));
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
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

        assertThat(tokenCookie).as("Cookie oms.token ausente após login").isNotNull();
        return tokenCookie.getValue();
    }

    private UUID createUser(String email, String name, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "email", email,
                "name", name,
                "password", password,
                "role", "USER"
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
}
