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
 * Doanh's variant + volume-tier IT. Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class VariantApiIntegrationTest {

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

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    @Test
    void create_withTiers_visibleOnProductGet() throws Exception {
        long productId = createProduct();
        String sku = uniqueSku("SKU-CRT");

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(variantBody(
                                sku,
                                "Khay 1kg",
                                "150000",
                                8,
                                true,
                                List.of(
                                        Map.of("minQtyKhay", 20, "unitPrice", new BigDecimal("130000")),
                                        Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("140000")))))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/" + productId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(1))
                .andExpect(jsonPath("$.variants[0].sku").value(sku))
                .andExpect(jsonPath("$.variants[0].packLabel").value("Khay 1kg"))
                .andExpect(jsonPath("$.variants[0].listPrice").value(150000))
                .andExpect(jsonPath("$.variants[0].packPerCase").value(8))
                .andExpect(jsonPath("$.variants[0].tiers.length()").value(2))
                .andExpect(jsonPath("$.variants[0].tiers[0].minQtyKhay").value(10))
                .andExpect(jsonPath("$.variants[0].tiers[0].unitPrice").value(140000))
                .andExpect(jsonPath("$.variants[0].tiers[1].minQtyKhay").value(20))
                .andExpect(jsonPath("$.variants[0].tiers[1].unitPrice").value(130000));
    }

    @Test
    void update_replacesTiersAndFields() throws Exception {
        long productId = createProduct();
        String sku = uniqueSku("SKU-UPD");
        long variantId = createVariantReturningId(productId, sku, "100000", List.of(
                Map.of("minQtyKhay", 5, "unitPrice", new BigDecimal("90000"))));

        String newSku = uniqueSku("SKU-NEW");
        mockMvc.perform(put("/variants/" + variantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(variantBody(
                                newSku,
                                "Khay 2kg",
                                "200000",
                                12,
                                false,
                                List.of(
                                        Map.of("minQtyKhay", 15, "unitPrice", new BigDecimal("180000")),
                                        Map.of("minQtyKhay", 30, "unitPrice", new BigDecimal("160000")))))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/" + productId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].id").value(variantId))
                .andExpect(jsonPath("$.variants[0].sku").value(newSku))
                .andExpect(jsonPath("$.variants[0].packLabel").value("Khay 2kg"))
                .andExpect(jsonPath("$.variants[0].listPrice").value(200000))
                .andExpect(jsonPath("$.variants[0].isActive").value(false))
                .andExpect(jsonPath("$.variants[0].tiers.length()").value(2))
                .andExpect(jsonPath("$.variants[0].tiers[0].minQtyKhay").value(15))
                .andExpect(jsonPath("$.variants[0].tiers[1].minQtyKhay").value(30));
    }

    @Test
    void create_duplicateSku_returns409() throws Exception {
        long productA = createProduct();
        long productB = createProduct();
        String sku = uniqueSku("SKU-DUP");
        createVariant(productA, sku, "100000", List.of());

        mockMvc.perform(post("/products/" + productB + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                variantBody(sku.toLowerCase(), "Khay", "100000", 1, true, List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_unknownProduct_returns404() throws Exception {
        mockMvc.perform(post("/products/9999999/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                variantBody(uniqueSku("SKU-404"), "Khay", "100000", 1, true, List.of()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy sản phẩm"));
    }

    @Test
    void update_unknownVariant_returns404() throws Exception {
        mockMvc.perform(put("/variants/9999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                variantBody(uniqueSku("SKU-404U"), "Khay", "100000", 1, true, List.of()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy quy cách"));
    }

    @Test
    void create_invalidListPrice_returns400() throws Exception {
        long productId = createProduct();

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                variantBody(uniqueSku("SKU-LP"), "Khay", "0", 1, true, List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_tierPriceAboveList_returns400() throws Exception {
        long productId = createProduct();

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(variantBody(
                                uniqueSku("SKU-TIER"),
                                "Khay",
                                "100000",
                                1,
                                true,
                                List.of(Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("100001")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá bậc không được lớn hơn giá niêm yết"));
    }

    @Test
    void create_duplicateTierMinQty_returns400() throws Exception {
        long productId = createProduct();

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(variantBody(
                                uniqueSku("SKU-TQ"),
                                "Khay",
                                "100000",
                                1,
                                true,
                                List.of(
                                        Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("90000")),
                                        Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("80000")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Các bậc số lượng không được trùng nhau"));
    }

    @Test
    void create_increasingTierPrices_returns400() throws Exception {
        long productId = createProduct();

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(variantBody(
                                uniqueSku("SKU-TI"),
                                "Khay",
                                "100000",
                                1,
                                true,
                                List.of(
                                        Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("90000")),
                                        Map.of("minQtyKhay", 20, "unitPrice", new BigDecimal("95000")))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Giá bậc sau phải nhỏ hơn hoặc bằng bậc trước"));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        long productId = createProduct();

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                variantBody(uniqueSku("SKU-401"), "Khay", "100000", 1, true, List.of()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withoutProductsPermission_returns403() throws Exception {
        long productId = createProduct();
        String token = login(ensureNoProductsUser(), NO_PRODUCTS_PASSWORD);

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .content(objectMapper.writeValueAsString(
                                variantBody(uniqueSku("SKU-403"), "Khay", "100000", 1, true, List.of()))))
                .andExpect(status().isForbidden());
    }

    private long createProduct() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", uniqueCode("P-VAR"));
        body.put("name", "SP variant IT " + SEQ.get());
        body.put("categoryId", null);
        body.put("description", null);
        body.put("storageNote", null);
        body.put("origin", null);
        body.put("imageUrl", null);
        body.put("gallery", null);
        body.put("isPublished", false);
        body.put("isActive", true);

        String response = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createVariant(long productId, String sku, String listPrice, List<Map<String, Object>> tiers)
            throws Exception {
        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                variantBody(sku, "Khay 0,5kg", listPrice, 10, true, tiers))))
                .andExpect(status().isNoContent());
    }

    private long createVariantReturningId(long productId, String sku, String listPrice, List<Map<String, Object>> tiers)
            throws Exception {
        createVariant(productId, sku, listPrice, tiers);
        String response = mockMvc.perform(get("/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("variants").get(0).get("id").asLong();
    }

    private static Map<String, Object> variantBody(
            String sku,
            String packLabel,
            String listPrice,
            int packPerCase,
            boolean active,
            List<Map<String, Object>> tiers) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sku", sku);
        map.put("packLabel", packLabel);
        map.put("unitWeightG", 500);
        map.put("listPrice", new BigDecimal(listPrice));
        map.put("packPerCase", packPerCase);
        map.put("barcode", null);
        map.put("isActive", active);
        map.put("tiers", tiers);
        return map;
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
