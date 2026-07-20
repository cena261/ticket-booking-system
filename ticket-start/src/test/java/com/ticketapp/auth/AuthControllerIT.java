package com.ticketapp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketapp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode registerTokens(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new Credentials(email, password));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("result");
    }

    private String register(String email, String password) throws Exception {
        return registerTokens(email, password).get("accessToken").asText();
    }

    private String refreshBody(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(new RefreshToken(refreshToken));
    }

    @Test
    void registerThenLoginReturnsTokens() throws Exception {
        String email = UUID.randomUUID() + "@demo.local";
        String password = "password123";
        register(email, password);

        String body = objectMapper.writeValueAsString(new Credentials(email, password));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.refreshToken").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() throws Exception {
        String email = UUID.randomUUID() + "@demo.local";
        register(email, "password123");

        String body = objectMapper.writeValueAsString(new Credentials(email, "wrong-password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1005));

        String email = UUID.randomUUID() + "@demo.local";
        String token = register(email, "password123");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value(email))
                .andExpect(jsonPath("$.result.role").value("USER"));
    }

    @Test
    void adminRouteForbiddenForUserRole() throws Exception {
        String token = register(UUID.randomUUID() + "@demo.local", "password123");

        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    void refreshRotatesAndInvalidatesOldToken() throws Exception {
        String refreshToken = registerTokens(UUID.randomUUID() + "@demo.local", "password123")
                .get("refreshToken").asText();

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode tokens = registerTokens(UUID.randomUUID() + "@demo.local", "password123");
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh)))
                .andExpect(status().isOk());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refresh)))
                .andExpect(status().isUnauthorized());
    }

    private record Credentials(String email, String password) {
    }

    private record RefreshToken(String refreshToken) {
    }
}
