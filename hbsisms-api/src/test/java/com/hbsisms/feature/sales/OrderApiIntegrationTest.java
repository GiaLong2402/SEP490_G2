package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
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
 * Doanh — IT Orders + Payments (Inter 2): create/list/detail/update/transition/cancel/pay/receipt.
 * Không Docker: Postgres từ {@code .env} / profile {@code it-external}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class OrderApiIntegrationTest {

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
    private SalesOrderRepository salesOrderRepository;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    private String salesToken;
    private Customer customer;
    private ProductVariant variant;

    @BeforeEach
    void setUp() throws Exception {
        String adminToken = login("admin", adminPassword);
        String salesUser = "sales_it_" + SEQ.getAndIncrement() + "_" + UUID.randomUUID().toString().substring(0, 6);
        createSalesUser(adminToken, salesUser);
        salesToken = login(salesUser, "Passw0rd!");

        customer = customerRepository.findAll().stream()
                .filter(c -> "KH-001".equals(c.getCode()) || c.getCustomerType() != null)
                .filter(c -> c.isActive())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cần ít nhất một khách active (KH-001)"));
        variant = productVariantRepository.findAll().stream()
                .filter(v -> "TOM-SU-05".equals(v.getSku()))
                .findFirst()
                .or(() -> productVariantRepository.findAll().stream().filter(ProductVariant::isActive).findFirst())
                .orElseThrow(() -> new IllegalStateException("Cần ít nhất một quy cách active"));
    }

    @Test
    void orderLifecycle_createGetUpdatePayTransitionCancelAndReceipt() throws Exception {
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("SO-\\d{4}-\\d{4}")))
                .andExpect(jsonPath("$.state").value("confirmed"))
                .andReturn();

        JsonNode order = objectMapper.readTree(created.getResponse().getContentAsString());
        long orderId = order.get("id").asLong();
        String code = order.get("code").asText();

        mockMvc.perform(get("/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.lines.length()").value(1));

        mockMvc.perform(get("/orders")
                        .param("q", code)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value(code));

        mockMvc.perform(put("/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].qty").value(2));

        mockMvc.perform(get("/orders/" + orderId + "/receipt")
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isConflict());

        JsonNode afterUpdate = objectMapper.readTree(mockMvc.perform(get("/orders/" + orderId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String total = afterUpdate.get("total").asText();

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(paymentBody(orderId, total))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.method").value("cash"));

        mockMvc.perform(get("/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("paid"));

        mockMvc.perform(get("/orders/" + orderId + "/receipt")
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value((int) orderId))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.paymentMethod").value("cash"))
                .andExpect(jsonPath("$.lines.length()").value(1));

        MvcResult preparing = mockMvc.perform(post("/orders/" + orderId + "/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(Map.of("state", "preparing"))))
                .andReturn();
        int preparingStatus = preparing.getResponse().getStatus();
        assertThat(preparingStatus == 204 || preparingStatus == 409).isTrue();

        MvcResult second = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isCreated())
                .andReturn();
        long cancelId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/orders/" + cancelId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(Map.of("reason", "IT hủy đơn"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orders/" + cancelId).header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("cancelled"));

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(paymentBody(cancelId, "1000"))))
                .andExpect(status().isConflict());
    }

    @Test
    void update_cancelledOrder_returns409() throws Exception {
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/orders/" + orderId + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Không sửa được"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isConflict());
    }

    @Test
    void list_filtersByAllocationStatus_fullExcludesIncoming() throws Exception {
        MvcResult fullCreated = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult incomingCreated = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(orderBody(1))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode fullNode = objectMapper.readTree(fullCreated.getResponse().getContentAsString());
        JsonNode incomingNode = objectMapper.readTree(incomingCreated.getResponse().getContentAsString());
        long fullId = fullNode.get("id").asLong();
        long incomingId = incomingNode.get("id").asLong();
        String fullCode = fullNode.get("code").asText();
        String incomingCode = incomingNode.get("code").asText();

        SalesOrder fullOrder = salesOrderRepository.findById(fullId).orElseThrow();
        fullOrder.setAllocationStatus(AllocationStatus.FULL);
        salesOrderRepository.save(fullOrder);

        SalesOrder incomingOrder = salesOrderRepository.findById(incomingId).orElseThrow();
        incomingOrder.setAllocationStatus(AllocationStatus.INCOMING);
        salesOrderRepository.save(incomingOrder);

        MvcResult listed = mockMvc.perform(get("/orders")
                        .param("allocationStatus", "full")
                        .param("pageSize", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
        assertThat(items.isArray()).isTrue();
        boolean sawFull = false;
        for (JsonNode item : items) {
            assertThat(item.get("allocationStatus").asText()).isEqualTo("full");
            assertThat(item.get("code").asText()).isNotEqualTo(incomingCode);
            if (fullCode.equals(item.get("code").asText())) {
                sawFull = true;
            }
        }
        assertThat(sawFull).isTrue();
    }

    private Map<String, Object> orderBody(int qty) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("variantId", variant.getId());
        line.put("unit", "pack");
        line.put("qty", qty);
        line.put("manualPrice", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer.getId());
        body.put("orderType", "retail");
        body.put("channel", "phone");
        body.put("paymentMethod", "cash");
        body.put("fulfillmentMethod", "pickup");
        body.put("shipAddressId", null);
        body.put("shippingFee", 0);
        body.put("discountTotal", 0);
        body.put("voucherCode", null);
        body.put("note", "IT order");
        body.put("lines", List.of(line));
        return body;
    }

    private Map<String, Object> paymentBody(long orderId, String amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer.getId());
        body.put("orderId", orderId);
        body.put("method", "cash");
        body.put("amount", amount);
        body.put("reference", null);
        body.put("note", "IT pay");
        return body;
    }

    private void createSalesUser(String adminToken, String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Sales IT");
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
}
