package com.hbsisms.feature.sales;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.inventory.StockRepository;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Cường — SalesRequest IT (Inter 2). Profile {@code it-external}, không Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class SalesRequestApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final int SALES_ROLE_ID = 3;
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private StockRepository stockRepository;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    private String salesToken;
    private Customer customer;
    private ProductVariant variant;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = login("admin", adminPassword);
        String salesUser = "sales_req_it_" + SEQ.getAndIncrement() + "_"
                + UUID.randomUUID().toString().substring(0, 6);
        createSalesUser(adminToken, salesUser);
        salesToken = login(salesUser, "Passw0rd!");

        customer = customerRepository.findAll().stream()
                .filter(Customer::isActive)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cần ít nhất một khách active"));
        variant = pickVariantWithStock();
    }

    private ProductVariant pickVariantWithStock() {
        LocalDate today = LocalDate.now();
        return productVariantRepository.findAll().stream()
                .filter(ProductVariant::isActive)
                .filter(v -> stockRepository.sumAvailableByVariantExcludingExpired(v.getId(), today) > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cần quy cách còn tồn khả dụng"));
    }

    @Test
    void flow_createListGet() throws Exception {
        String summary = "IT yêu cầu " + uniqueSuffix();
        MvcResult created = mockMvc.perform(post("/sales-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(requestBody(summary))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("new"))
                .andExpect(jsonPath("$.summary").value(summary))
                .andReturn();

        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();
        String code = node.get("code").asText();

        mockMvc.perform(get("/sales-requests")
                        .param("q", code)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].code", hasItem(code)));

        mockMvc.perform(get("/sales-requests/" + id).header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.summary").value(summary));
    }

    @Test
    void reject_setsRejectedWithReason() throws Exception {
        MvcResult created = mockMvc.perform(post("/sales-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(requestBody("IT reject " + uniqueSuffix()))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/sales-requests/" + id + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(Map.of("rejectReason", "Hết hàng lâu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectReason").value("Hết hàng lâu"))
                .andExpect(jsonPath("$.feasible").value(false));
    }

    @Test
    void feasibility_customerAccepted_createsOrder() throws Exception {
        MvcResult created = mockMvc.perform(post("/sales-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(requestBody("IT feasible " + uniqueSuffix()))))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("feasible", true);
        body.put("fulfillableQty", 1);
        body.put("shortageQty", 0);
        body.put("expectedDate", null);
        body.put("note", "IT accept");
        body.put("customerAccepted", true);
        body.put("order", orderBody());

        mockMvc.perform(post("/sales-requests/" + id + "/feasibility")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.status").value("ordered"))
                .andExpect(jsonPath("$.request.feasible").value(true))
                .andExpect(jsonPath("$.order.id").exists())
                .andExpect(jsonPath("$.order.code").exists());
    }

    private Map<String, Object> requestBody(String summary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer.getId());
        body.put("customerName", null);
        body.put("channel", "phone");
        body.put("summary", summary);
        body.put("neededDate", null);
        body.put("requestedQty", 1);
        body.put("availableQty", null);
        return body;
    }

    private Map<String, Object> orderBody() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("variantId", variant.getId());
        line.put("unit", "pack");
        line.put("qty", 1);
        line.put("manualPrice", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer.getId());
        body.put("customerName", null);
        body.put("orderType", "retail");
        body.put("channel", "phone");
        body.put("paymentMethod", "cash");
        body.put("fulfillmentMethod", "pickup");
        body.put("shipAddressId", null);
        body.put("shippingFee", 0);
        body.put("discountTotal", 0);
        body.put("voucherCode", null);
        body.put("note", "IT from request");
        body.put("lines", List.of(line));
        return body;
    }

    private void createSalesUser(String adminToken, String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Sales Request IT");
        body.put("email", null);
        body.put("phone", "09" + String.format("%08d", Math.floorMod(username.hashCode(), 100_000_000)));
        body.put("roleId", SALES_ROLE_ID);
        body.put("position", "Kinh doanh");
        body.put("employeeCode", null);
        body.put("isActive", true);
        body.put("password", "Passw0rd!");
        mockMvc.perform(post("/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
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

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
