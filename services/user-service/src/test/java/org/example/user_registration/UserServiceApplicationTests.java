package org.example.user_registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserServiceApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void internalRegistrationAndCredentialVerificationWork() throws Exception {
        String email = "microservice-user@example.com";
        mockMvc.perform(post("/internal/auth/users")
                        .header("X-Internal-Api-Key", "test-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Service",
                                  "lastName": "User",
                                  "email": "microservice-user@example.com",
                                  "mobile": "1234567890",
                                  "password": "Password@123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/internal/auth/verify")
                        .header("X-Internal-Api-Key", "test-internal-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"microservice-user@example.com","password":"Password@123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void userApiRequiresAValidAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(token -> token
                                .subject("user-uuid")
                                .claim("token_type", "access"))))
                .andExpect(status().isOk());
    }

    @Test
    void internalApiRejectsInvalidApiKeys() throws Exception {
        mockMvc.perform(post("/internal/auth/verify")
                        .header("X-Internal-Api-Key", "invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":"Password@123"}
                                """))
                .andExpect(status().isForbidden());
    }
}
