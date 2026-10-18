package org.example.user_registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRegistrationApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationLoginAndProtectedUserListingWork() throws Exception {
        String registration = """
                {
                  "firstName": "Sudarshan",
                  "lastName": "Dasari",
                  "email": "sudarshan@example.com",
                  "mobile": "9999999999",
                  "password": "Password@123"
                }
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration))
                .andExpect(status().isCreated());

        String login = """
                {"email":"sudarshan@example.com","password":"Password@123"}
                """;
        String tokenResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode token = objectMapper.readTree(tokenResponse);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token.get("accessToken").asText()))
                .andExpect(status().isOk());

        String authorization = "Bearer " + token.get("accessToken").asText();
        String createUser = """
                {
                  "firstName": "Test",
                  "lastName": "User",
                  "email": "test.user@example.com",
                  "mobile": "1234567890",
                  "password": "Password@123"
                }
                """;
        String userResponse = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(userResponse).get("id").asText();

        mockMvc.perform(get("/api/v1/users/search")
                        .param("email", "test.user@example.com")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Updated",
                                  "lastName": "User",
                                  "email": "test.user@example.com",
                                  "mobile": "1234567890"
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/users/" + userId)
                        .header("Authorization", authorization))
                .andExpect(status().isNoContent());

        String refreshRequest = objectMapper.createObjectNode()
                .put("refreshToken", token.get("refreshToken").asText()).toString();
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest))
                .andExpect(status().isUnauthorized());
    }
}
