package com.hbsisms;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end auth + admin user flows against a real PostgreSQL (schema generated from entities). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthAndUserApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final int SALES_ROLE_ID = 3;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    @Test
    void me_returnsCurrentUserWithPermissions() throws Exception {
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roleCode").value("admin"))
                .andExpect(jsonPath("$.permissions", hasItem("admin")));
    }

    @Test
    void protectedEndpoint_withoutToken_returns401Json() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void login_wrongPassword_returns400() throws Exception {
        String username = createSalesUser(uniqueName());

        postJson("/auth/login", Map.of("username", username, "password", "wrong-password"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));
    }

    @Test
    void logout_revokesToken() throws Exception {
        String token = login(createSalesUser(uniqueName()), "Passw0rd!");

        postJson("/auth/logout", null, token).andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createUser_thenListAndDuplicateIsRejected() throws Exception {
        String username = uniqueName();
        postJson("/admin/users", userBody(username.toUpperCase(), true, "Passw0rd!"), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.userType").value("staff"))
                .andExpect(jsonPath("$.roleName").value("Nhân viên bán hàng"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/admin/users").param("q", username).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.items[0].username").value(username));

        postJson("/admin/users", userBody(username, true, "Passw0rd!"), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void createUser_duplicatePhone_isRejected() throws Exception {
        String phone = "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Map<String, Object> first = userBody(uniqueName(), true, "Passw0rd!");
        first.put("phone", phone);
        postJson("/admin/users", first, adminToken).andExpect(status().isCreated());

        Map<String, Object> second = userBody(uniqueName(), true, "Passw0rd!");
        second.put("phone", phone);
        postJson("/admin/users", second, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Số điện thoại " + phone + " đã tồn tại"));
    }

    @Test
    void createUser_invalidBody_returnsFieldErrors() throws Exception {
        postJson("/admin/users", userBody("x", true, "short"), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void adminEndpoints_requireAdminPermission() throws Exception {
        String salesToken = login(createSalesUser(uniqueName()), "Passw0rd!");

        mockMvc.perform(get("/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatingUser_invalidatesTheirToken() throws Exception {
        String username = uniqueName();
        long id = createSalesUserId(username);
        String salesToken = login(username, "Passw0rd!");

        putJson("/admin/users/" + id, userBody(username, false, null), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isUnauthorized());
        postJson("/auth/login", Map.of("username", username, "password", "Passw0rd!"), null)
                .andExpect(status().isForbidden());
    }

    @Test
    void createUser_withCustomerRole_isRejected() throws Exception {
        Map<String, Object> body = userBody(uniqueName(), true, "Passw0rd!");
        body.put("roleId", 5);

        postJson("/admin/users", body, adminToken).andExpect(status().isBadRequest());
    }

    @Test
    void login_isBlockedAfterTooManyFailures() throws Exception {
        String username = createSalesUser(uniqueName());
        for (int i = 0; i < 5; i++) {
            postJson("/auth/login", Map.of("username", username, "password", "wrong-password"), null)
                    .andExpect(status().isBadRequest());
        }

        postJson("/auth/login", Map.of("username", username, "password", "Passw0rd!"), null)
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void roles_listExcludesCustomer() throws Exception {
        mockMvc.perform(get("/admin/roles").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].code").value("admin"));
    }

    private String login(String username, String password) throws Exception {
        String body = postJson("/auth/login", Map.of("username", username, "password", password), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String createSalesUser(String username) throws Exception {
        createSalesUserId(username);
        return username;
    }

    private long createSalesUserId(String username) throws Exception {
        String body = postJson("/admin/users", userBody(username, true, "Passw0rd!"), adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asLong();
    }

    private static Map<String, Object> userBody(String username, boolean active, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Nguyễn Thị Mai");
        body.put("email", null);
        body.put("phone", null);
        body.put("roleId", SALES_ROLE_ID);
        body.put("position", "Kinh doanh");
        body.put("employeeCode", null);
        body.put("isActive", active);
        body.put("password", password);
        return body;
    }

    private ResultActions postJson(String path, Object body, String token) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request.content(objectMapper.writeValueAsString(body));
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        return mockMvc.perform(request);
    }

    private ResultActions putJson(String path, Object body, String token) throws Exception {
        return mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueName() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
