package com.hbsisms.feature.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Doanh's product write IT: create → get → update, publish rules, conflict/auth.
 * Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProductApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String NO_PRODUCTS_PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CategoryRepository categoryRepository;

    private String adminToken;
    private Long demoCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
        demoCategoryId = categoryRepository.findAll().stream()
                .filter(c -> "DEMO-SEA".equals(c.getCode()))
                .map(Category::getId)
                .findFirst()
                .orElse(null);
    }

    @Test
    void flow_createGetUpdate() throws Exception {
        String code = uniqueCode("P-FLOW");
        String body = objectMapper.writeValueAsString(productBody(
                code,
                "Tôm IT flow",
                demoCategoryId,
                "https://cdn.example/main.jpg",
                List.of("https://cdn.example/g1.jpg"),
                false,
                true));

        String created = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Tôm IT flow"))
                .andExpect(jsonPath("$.slug").value("tom-it-flow"))
                .andExpect(jsonPath("$.isPublished").value(false))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.example/main.jpg"))
                .andExpect(jsonPath("$.gallery[0]").value("https://cdn.example/g1.jpg"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/products/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("Tôm IT flow"));

        String updatedCode = uniqueCode("P-UPD");
        mockMvc.perform(put("/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(productBody(
                                updatedCode,
                                "Tôm IT đã sửa",
                                demoCategoryId,
                                "https://cdn.example/main2.jpg",
                                List.of(),
                                false,
                                true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(updatedCode))
                .andExpect(jsonPath("$.name").value("Tôm IT đã sửa"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.example/main2.jpg"))
                .andExpect(jsonPath("$.gallery").isEmpty())
                .andExpect(jsonPath("$.slug").value("tom-it-flow"));

        mockMvc.perform(get("/products/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(updatedCode))
                .andExpect(jsonPath("$.name").value("Tôm IT đã sửa"));
    }

    @Test
    void publish_withoutActiveVariant_returns400() throws Exception {
        long id = createProduct(uniqueCode("P-PUB0"), "Chưa có variant", false);

        mockMvc.perform(put("/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-PUB0"), "Chưa có variant", demoCategoryId, null, null, true, true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể hiển thị cổng B2C khi chưa có quy cách đang bán"));
    }

    @Test
    void publish_withActiveVariant_succeeds() throws Exception {
        String code = uniqueCode("P-PUB1");
        long id = createProduct(code, "Có variant", false);
        createVariant(id, uniqueSku("SKU-PUB"));

        mockMvc.perform(put("/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(code, "Có variant", demoCategoryId, null, null, true, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublished").value(true));
    }

    @Test
    void create_duplicateCode_returns409() throws Exception {
        String code = uniqueCode("P-DUP");
        createProduct(code, "Lần một", false);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(code.toLowerCase(), "Lần hai", null, null, null, false, true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-401"), "No auth", null, null, null, false, true))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withoutProductsPermission_returns403() throws Exception {
        String token = login(ensureNoProductsUser(), NO_PRODUCTS_PASSWORD);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-403"), "Forbidden", null, null, null, false, true))))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_unknownId_returns404() throws Exception {
        mockMvc.perform(put("/products/9999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-404"), "Missing", null, null, null, false, true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm"));
    }

    @Test
    void create_unknownCategory_returns404() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-CAT"), "Sai loại", 9_999_999L, null, null, false, true))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy loại sản phẩm"));
    }

    @Test
    void create_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody("  ", "Tên ok", null, null, null, false, true))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_blankName_returns400() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(uniqueCode("P-BN"), "  ", null, null, null, false, true))))
                .andExpect(status().isBadRequest());
    }

    private long createProduct(String code, String name, boolean published) throws Exception {
        String body = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                productBody(code, name, demoCategoryId, null, null, published, true))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void createVariant(long productId, String sku) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("packLabel", "Khay 0,5kg");
        body.put("unitWeightG", 500);
        body.put("listPrice", new BigDecimal("100000"));
        body.put("packPerCase", 10);
        body.put("barcode", null);
        body.put("isActive", true);
        body.put("tiers", List.of());

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    private String ensureNoProductsUser() {
        AppRole role = roleRepository.findByCode("it-no-products").orElseGet(() -> {
            AppRole created = new AppRole("it-no-products", "IT không products", Set.of(Permission.ORDERS));
            return roleRepository.save(created);
        });

        String username = "itnoprod";
        return userRepository.findByUsername(username).map(AppUser::getUsername).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setFullName("IT No Products");
            user.setPasswordHash(passwordEncoder.encode(NO_PRODUCTS_PASSWORD));
            user.setUserType(UserType.STAFF);
            user.setRole(role);
            user.setActive(true);
            userRepository.save(user);
            return username;
        });
    }

    private static Map<String, Object> productBody(
            String code,
            String name,
            Long categoryId,
            String imageUrl,
            List<String> gallery,
            boolean published,
            boolean active) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("categoryId", categoryId);
        map.put("description", null);
        map.put("storageNote", null);
        map.put("origin", null);
        map.put("imageUrl", imageUrl);
        map.put("gallery", gallery);
        map.put("isPublished", published);
        map.put("isActive", active);
        return map;
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }

    private static String uniqueSku(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
