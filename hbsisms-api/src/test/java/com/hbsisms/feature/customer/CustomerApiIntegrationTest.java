package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Duy — IT API CRUD/B2C khách hàng (task 24): create/update/lock + quy tắc credit.
 * Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerApiIntegrationTest {

    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
    }

    @Test
    void create_b2c_assignsKhCodeAndZeroDebt() throws Exception {
        int n = SEQ.getAndIncrement();
        MvcResult result = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2cBody("IT B2C " + n, phone(n)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType").value("b2c"))
                .andExpect(jsonPath("$.currentDebt").value(0))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("KH-\\d{3}")))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("creditLimit").decimalValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.get("netDays").asInt()).isZero();
    }

    @Test
    void create_b2b_keepsCreditTaxNetDays() throws Exception {
        int n = SEQ.getAndIncrement();
        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2bBody(
                                "IT B2B " + n,
                                phone(n),
                                "0200" + String.format("%06d", n % 1_000_000),
                                "5000000",
                                15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerType").value("b2b"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("KH-B\\d{3}")))
                .andExpect(jsonPath("$.creditLimit").value(5_000_000))
                .andExpect(jsonPath("$.netDays").value(15))
                .andExpect(jsonPath("$.taxCode").value("0200" + String.format("%06d", n % 1_000_000)));
    }

    @Test
    void create_b2c_withCreditLimit_returns400() throws Exception {
        int n = SEQ.getAndIncrement();
        Map<String, Object> body = b2cBody("IT B2C credit " + n, phone(n));
        body.put("creditLimit", 1000);

        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_b2bToB2c_withDebt_returns409() throws Exception {
        int n = SEQ.getAndIncrement();
        MvcResult created = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2bBody(
                                "IT B2B debt " + n,
                                phone(n),
                                "0300" + String.format("%06d", n % 1_000_000),
                                "1000000",
                                7))))
                .andExpect(status().isCreated())
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        Customer customer = customerRepository.findById(id).orElseThrow();
        customer.setCurrentDebt(new BigDecimal("2500000"));
        customerRepository.saveAndFlush(customer);

        mockMvc.perform(put("/customers/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2cBody("IT B2B debt " + n, phone(n)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Khách đang có công nợ, không thể chuyển sang B2C"));
    }

    @Test
    void create_invalidPhone_returns400() throws Exception {
        int n = SEQ.getAndIncrement();
        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2cBody("IT bad phone " + n, "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_b2b_invalidTaxCode_returns400() throws Exception {
        int n = SEQ.getAndIncrement();
        mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                b2bBody("IT bad tax " + n, phone(n), "ABC", "0", 0))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setActive_lockAndUnlock() throws Exception {
        int n = SEQ.getAndIncrement();
        MvcResult created = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2cBody("IT lock " + n, phone(n)))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(patch("/customers/" + id + "/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(get("/customers/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(patch("/customers/" + id + "/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("isActive", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void get_unknown_returns404() throws Exception {
        mockMvc.perform(get("/customers/9999999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy khách hàng"));
    }

    @Test
    void list_filtersByActive() throws Exception {
        int n = SEQ.getAndIncrement();
        MvcResult created = mockMvc.perform(post("/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(b2cBody("IT list " + n, phone(n)))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String code = objectMapper.readTree(created.getResponse().getContentAsString()).get("code").asText();

        mockMvc.perform(patch("/customers/" + id + "/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("isActive", false))))
                .andExpect(status().isOk());

        String listBody = mockMvc.perform(get("/customers")
                        .param("q", code)
                        .param("isActive", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode items = objectMapper.readTree(listBody).get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.toString()).contains(code);
    }

    private static Map<String, Object> b2cBody(String name, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerType", "b2c");
        body.put("name", name);
        body.put("phone", phone);
        body.put("creditLimit", 0);
        body.put("netDays", 0);
        body.put("isActive", true);
        return body;
    }

    private static Map<String, Object> b2bBody(
            String name, String phone, String taxCode, String creditLimit, int netDays) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerType", "b2b");
        body.put("name", name);
        body.put("phone", phone);
        body.put("taxCode", taxCode);
        body.put("contactName", "IT Contact");
        body.put("contactPhone", phone);
        body.put("creditLimit", new BigDecimal(creditLimit));
        body.put("netDays", netDays);
        body.put("isActive", true);
        return body;
    }

    /** Unique VN mobile: 09xxxxxxxx from sequence. */
    private static String phone(int n) {
        return String.format("09%08d", n % 100_000_000);
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
}
