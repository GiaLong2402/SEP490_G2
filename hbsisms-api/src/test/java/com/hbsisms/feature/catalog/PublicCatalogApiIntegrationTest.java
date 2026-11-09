package com.hbsisms.feature.catalog;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guest (anonymous) public catalog: published-only list, binary availability, unpublished slug 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class PublicCatalogApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    private String adminToken;
    private Long demoCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", adminPassword);
        demoCategoryId = categoryRepository.findAll().stream()
                .filter(c -> "DEMO-SEA".equals(c.getCode()) || "CAT-TOM".equals(c.getCode()))
                .map(Category::getId)
                .findFirst()
                .orElseGet(() -> categoryRepository.findAll().stream()
                        .map(Category::getId)
                        .findFirst()
                        .orElse(null));
    }

    @Test
    void guestList_hidesUnpublished_andShowsPublished() throws Exception {
        String draftCode = uniqueCode("P-DRF");
        String liveCode = uniqueCode("P-LIVE");
        String liveName = "Tom public " + SEQ.getAndIncrement();

        createProduct(draftCode, "Draft hidden " + draftCode, false);

        long liveId = createProduct(liveCode, liveName, false);
        createVariant(liveId, uniqueSku("SKU-LIVE"));
        publish(liveId, liveCode, liveName);

        mockMvc.perform(get("/products").param("page", "1").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(liveCode)))
                .andExpect(jsonPath("$.items[*].code", not(hasItem(draftCode))));
    }

    @Test
    void guestList_availableIsBinary() throws Exception {
        mockMvc.perform(get("/products").param("page", "1").param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].variants[*].available", everyItem(lessThanOrEqualTo(1))));
    }

    @Test
    void guestGetBySlug_unpublished_returns404() throws Exception {
        String code = uniqueCode("P-SLUG");
        String name = "Slug draft " + SEQ.getAndIncrement();
        long id = createProduct(code, name, false);
        createVariant(id, uniqueSku("SKU-SLUG"));

        String body = mockMvc.perform(get("/products/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String slug = objectMapper.readTree(body).get("slug").asText();

        mockMvc.perform(get("/products/slug/" + slug)).andExpect(status().isNotFound());
    }

    @Test
    void guestGetBySlug_published_ok() throws Exception {
        String code = uniqueCode("P-OK");
        String name = "Slug live " + SEQ.getAndIncrement();
        long id = createProduct(code, name, false);
        createVariant(id, uniqueSku("SKU-OK"));
        publish(id, code, name);

        String body = mockMvc.perform(get("/products/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String slug = objectMapper.readTree(body).get("slug").asText();

        mockMvc.perform(get("/products/slug/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.isPublished").value(true));
    }

    @Test
    void staffList_stillSeesDraft() throws Exception {
        String draftCode = uniqueCode("P-STF");
        createProduct(draftCode, "Staff draft " + draftCode, false);

        mockMvc.perform(get("/products")
                        .param("q", draftCode)
                        .param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(draftCode)));
    }

    private long createProduct(String code, String name, boolean published) throws Exception {
        String body = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(productBody(code, name, published))))
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
        body.put("listPrice", new BigDecimal("189000"));
        body.put("packPerCase", 10);
        body.put("barcode", null);
        body.put("isActive", true);
        body.put("tiers", List.of(
                Map.of("minQtyKhay", 10, "unitPrice", new BigDecimal("175000")),
                Map.of("minQtyKhay", 50, "unitPrice", new BigDecimal("160000"))));

        mockMvc.perform(post("/products/" + productId + "/variants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNoContent());
    }

    private void publish(long id, String code, String name) throws Exception {
        mockMvc.perform(put("/products/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(productBody(code, name, true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPublished").value(true));
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

    private Map<String, Object> productBody(String code, String name, boolean published) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("categoryId", demoCategoryId);
        map.put("description", null);
        map.put("storageNote", null);
        map.put("origin", null);
        map.put("imageUrl", null);
        map.put("gallery", List.of());
        map.put("isPublished", published);
        map.put("isActive", true);
        return map;
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
