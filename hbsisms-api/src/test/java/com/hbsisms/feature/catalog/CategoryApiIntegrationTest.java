package com.hbsisms.feature.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cường — Category CRUD IT (không Docker).
 * Dùng Postgres từ {@code .env} / profile {@code it-external} ({@code ddl-auto=update}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class CategoryApiIntegrationTest {

    static {
        // Remote Postgres rejects JVM default Asia/Saigon as startup TimeZone.
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final String NO_PRODUCTS_PASSWORD = "Passw0rd!";
    private static final AtomicInteger SEQ = new AtomicInteger(1);

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

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", adminPassword);
    }

    @Test
    void flow_createListUpdate_showsParentNameAndProductCount() throws Exception {
        String parentCode = uniqueCode("CAT-P");
        String parentBody = mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                categoryBody(parentCode, "Loại cha " + parentCode, null, 1, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(parentCode))
                .andExpect(jsonPath("$.productCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long parentId = objectMapper.readTree(parentBody).get("id").asLong();

        String childCode = uniqueCode("CAT-C");
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                categoryBody(childCode, "Loại con", parentId, 2, true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parentId))
                .andExpect(jsonPath("$.parentName").value("Loại cha " + parentCode));

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem(parentCode)))
                .andExpect(jsonPath("$[*].code", hasItem(childCode)));

        putJson("/categories/" + parentId, categoryBody(parentCode, "Loại cha cập nhật", null, 5, false), adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Loại cha cập nhật"))
                .andExpect(jsonPath("$.sortOrder").value(5))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void create_duplicateCode_returns409() throws Exception {
        String code = uniqueCode("CAT-DUP");
        postJson("/categories", categoryBody(code, "Lần 1", null, 1, true), adminToken)
                .andExpect(status().isCreated());

        postJson("/categories", categoryBody(code.toUpperCase(), "Lần 2", null, 1, true), adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("đã tồn tại")));
    }

    @Test
    void update_selfAsParent_returns409() throws Exception {
        String code = uniqueCode("CAT-SELF");
        String created = postJson("/categories", categoryBody(code, "Self", null, 1, true), adminToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        putJson("/categories/" + id, categoryBody(code, "Self", id, 1, true), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void update_descendantAsParent_returns409() throws Exception {
        String rootCode = uniqueCode("CAT-R");
        String rootBody = postJson("/categories", categoryBody(rootCode, "Root", null, 1, true), adminToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long rootId = objectMapper.readTree(rootBody).get("id").asLong();

        String childBody = postJson(
                        "/categories", categoryBody(uniqueCode("CAT-CH"), "Child", rootId, 2, true), adminToken)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long childId = objectMapper.readTree(childBody).get("id").asLong();

        putJson("/categories/" + rootId, categoryBody(rootCode, "Root", childId, 1, true), adminToken)
                .andExpect(status().isConflict());
    }

    @Test
    void create_unknownParent_returns404() throws Exception {
        postJson("/categories", categoryBody(uniqueCode("CAT-NP"), "No parent", 999_999L, 1, true), adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void update_unknownCategory_returns404() throws Exception {
        putJson(
                        "/categories/999999",
                        categoryBody(uniqueCode("CAT-X"), "Missing", null, 1, true),
                        adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                categoryBody(uniqueCode("CAT-U"), "Unauthorized", null, 1, true))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_withoutProductsPermission_returns403() throws Exception {
        String username = ensureNoProductsUser();
        String token = login(username, NO_PRODUCTS_PASSWORD);

        mockMvc.perform(get("/categories").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    private String ensureNoProductsUser() {
        AppRole role = roleRepository.findByCode("it-no-products").orElseGet(() -> {
            AppRole created = new AppRole("it-no-products", "IT không products", Set.of(Permission.ORDERS));
            return roleRepository.save(created);
        });

        String username = "itnoprod-cat";
        return userRepository.findByUsername(username).map(AppUser::getUsername).orElseGet(() -> {
            AppUser user = new AppUser();
            user.setUsername(username);
            user.setFullName("IT No Products Cat");
            user.setPasswordHash(passwordEncoder.encode(NO_PRODUCTS_PASSWORD));
            user.setUserType(UserType.STAFF);
            user.setRole(role);
            user.setActive(true);
            userRepository.save(user);
            return username;
        });
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, Object body, String token)
            throws Exception {
        return mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions putJson(String path, Object body, String token)
            throws Exception {
        return mockMvc.perform(put(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
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

    private static Map<String, Object> categoryBody(
            String code, String name, Long parentId, int sortOrder, boolean active) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", code);
        map.put("name", name);
        map.put("description", null);
        map.put("parentId", parentId);
        map.put("sortOrder", sortOrder);
        map.put("isActive", active);
        return map;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
