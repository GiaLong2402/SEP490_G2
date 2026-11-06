package com.hbsisms.feature.catalog;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Cường — Product list/search/detail IT (không Docker).
 * Dùng Postgres từ {@code .env} / profile {@code it-external}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class ProductReadApiIntegrationTest {

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
                .filter(c -> "DEMO-SEA".equals(c.getCode()))
                .map(Category::getId)
                .findFirst()
                .orElse(null);
    }

    @Test
    void list_withoutFilter_returnsPage() throws Exception {
        mockMvc.perform(get("/products")
                        .param("page", "1")
                        .param("pageSize", "15")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(15))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(0)));
    }

    @Test
    void list_q_filtersByCodeOrName() throws Exception {
        String code = uniqueCode("P-Q");
        String name = "Tom search " + SEQ.getAndIncrement();
        createProduct(code, name);

        mockMvc.perform(get("/products")
                        .param("q", code)
                        .param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(code)));

        mockMvc.perform(get("/products")
                        .param("q", "Tom search")
                        .param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].name", hasItem(name)));
    }

    @Test
    void list_categoryId_includesDescendants() throws Exception {
        String rootCode = uniqueCode("CAT-R");
        String rootBody = mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(categoryBody(rootCode, "Root", null))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long rootId = objectMapper.readTree(rootBody).get("id").asLong();

        String childBody = mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                categoryBody(uniqueCode("CAT-C"), "Child", rootId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long childId = objectMapper.readTree(childBody).get("id").asLong();

        String rootProduct = uniqueCode("P-ROOT");
        String childProduct = uniqueCode("P-CHILD");
        createProduct(rootProduct, "SP root", rootId);
        createProduct(childProduct, "SP child", childId);

        mockMvc.perform(get("/products")
                        .param("categoryId", String.valueOf(rootId))
                        .param("page", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(rootProduct)))
                .andExpect(jsonPath("$.items[*].code", hasItem(childProduct)));
    }

    @Test
    void get_detail_returnsProduct() throws Exception {
        String code = uniqueCode("P-D");
        long id = createProduct(code, "Chi tiet " + code, demoCategoryId);

        mockMvc.perform(get("/products/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.variants").isArray());
    }

    @Test
    void get_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/products/999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    private long createProduct(String code, String name) throws Exception {
        return createProduct(code, name, demoCategoryId);
    }

    private long createProduct(String code, String name, Long categoryId) throws Exception {
        String body = mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(productBody(code, name, categoryId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
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

    private static Map<String, Object> productBody(String code, String name, Long categoryId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("categoryId", categoryId);
        map.put("description", null);
        map.put("storageNote", null);
        map.put("origin", null);
        map.put("imageUrl", null);
        map.put("gallery", List.of());
        map.put("isPublished", false);
        map.put("isActive", true);
        return map;
    }

    private static Map<String, Object> categoryBody(String code, String name, Long parentId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", null);
        map.put("parentId", parentId);
        map.put("sortOrder", 1);
        map.put("isActive", true);
        return map;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
