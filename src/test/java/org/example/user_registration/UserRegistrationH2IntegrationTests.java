package org.example.user_registration;

import org.example.user_registration.auth.RefreshToken;
import org.example.user_registration.auth.RefreshTokenRepository;
import org.example.user_registration.user.Role;
import org.example.user_registration.user.RoleRepository;
import org.example.user_registration.user.User;
import org.example.user_registration.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRegistrationH2IntegrationTests {
    private static final String PASSWORD = "Password@123";
    private static final String USER_EMAIL = "coverage.user@example.com";
    private static final String ADMIN_EMAIL = "coverage.admin@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void validatesRegistrationAndAllowsUsersToManageOnlyTheirOwnProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"","lastName":"User","email":"bad-email",
                                 "mobile":"123","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        register(USER_EMAIL);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(USER_EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(USER_EMAIL, "incorrect")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        User registeredUser = userRepository.findByEmailIgnoreCase(USER_EMAIL).orElseThrow();
        assertTrue(registeredUser.getEnabled());
        assertNotEquals(PASSWORD, registeredUser.getPassword());
        assertTrue(passwordEncoder.matches(PASSWORD, registeredUser.getPassword()));
        String accessToken = accessToken(login(USER_EMAIL, PASSWORD));
        String authorization = bearer(accessToken);
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", authorization))
                .andExpect(status().isForbidden());
        JsonNode profile = objectMapper.readTree(mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(USER_EMAIL, profile.get("email").asString());

        mockMvc.perform(get("/api/v1/users/" + profile.get("id").asString())
                        .header("Authorization", authorization))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":" Updated ","lastName":" Person ","mobile":"1234567890"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));

        String refreshToken = objectMapper.readTree(login(USER_EMAIL, PASSWORD))
                .get("refreshToken").asString();
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(refreshToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest("unknown-refresh-token")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsCanCreateSearchUpdateAndDeleteUsers() throws Exception {
        register(ADMIN_EMAIL);
        User admin = userRepository.findByEmailIgnoreCase(ADMIN_EMAIL).orElseThrow();
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        admin.getRoles().add(adminRole);
        userRepository.save(admin);
        String authorization = bearer(accessToken(login(ADMIN_EMAIL, PASSWORD)));

        mockMvc.perform(get("/api/v1/users").header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/search")
                        .param("email", "coverage")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users")
                        .param("email", " coverage ")
                        .header("Authorization", authorization))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userRequest("managed.user@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(created).get("id").asString();

        mockMvc.perform(get("/api/v1/users/" + userId).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("managed.user@example.com"));
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Managed","lastName":"Updated",
                                 "email":"MANAGED.USER@example.com","mobile":"1234567890"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userRequest("managed.user@example.com")))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Managed","lastName":"Updated",
                                 "email":"other@example.com","mobile":"1234567890"}
                                """))
                .andExpect(status().isOk());
        String duplicateId = objectMapper.readTree(created).get("id").asString();
        mockMvc.perform(put("/api/v1/users/" + duplicateId)
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Managed","lastName":"Updated",
                                 "email":"coverage.admin@example.com","mobile":"1234567890"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/users/" + userId).header("Authorization", authorization))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/" + userId).header("Authorization", authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rejectsExpiredAndDisabledRefreshTokensAndDisabledLogins() throws Exception {
        register(USER_EMAIL);
        User user = userRepository.findByEmailIgnoreCase(USER_EMAIL).orElseThrow();

        String expired = "expired-test-token";
        refreshTokenRepository.save(new RefreshToken(hash(expired), user, Instant.now().minusSeconds(1)));
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(expired)))
                .andExpect(status().isUnauthorized());

        String disabledToken = "disabled-test-token";
        refreshTokenRepository.save(new RefreshToken(hash(disabledToken), user, Instant.now().plusSeconds(60)));
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", user.getId());
        mockMvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshRequest(disabledToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(USER_EMAIL, PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration(email)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String accessToken(String loginResponse) throws Exception {
        return objectMapper.readTree(loginResponse).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String registration(String email) {
        return """
                {"firstName":"Coverage","lastName":"User","email":"%s",
                 "mobile":"1234567890","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private static String userRequest(String email) {
        return """
                {"firstName":"Managed","lastName":"User","email":"%s",
                 "mobile":"1234567890","password":"%s"}
                """.formatted(email, PASSWORD);
    }

    private static String loginRequest(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String refreshRequest(String token) {
        return """
                {"refreshToken":"%s"}
                """.formatted(token);
    }

    private static String hash(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
