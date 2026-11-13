package com.hbsisms.feature.sales;

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
import com.hbsisms.feature.payment.PaymentStatus;
import java.time.Instant;
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
 * Cường — Hold expire job IT (Inter 2). Profile {@code it-external}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
class HoldExpireApiIntegrationTest {

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
        String salesUser = "sales_hold_it_" + SEQ.getAndIncrement() + "_"
                + UUID.randomUUID().toString().substring(0, 6);
        createSalesUser(adminToken, salesUser);
        salesToken = login(salesUser, "Passw0rd!");

        customer = customerRepository.findAll().stream()
                .filter(Customer::isActive)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cần ít nhất một khách active"));
        LocalDate today = LocalDate.now();
        variant = productVariantRepository.findAll().stream()
                .filter(ProductVariant::isActive)
                .filter(v -> stockRepository.sumAvailableByVariantExcludingExpired(v.getId(), today) > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cần quy cách còn tồn khả dụng"));
    }

    @Test
    void run_expiresUnpaidPastHold_cancelsOrder() throws Exception {
        // WEB + unpaid để có hold window; seed holdExpiresAt quá khứ rồi gọi job.
        MvcResult created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .content(objectMapper.writeValueAsString(webOrderBody(1))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode orderNode = objectMapper.readTree(created.getResponse().getContentAsString());
        long orderId = orderNode.get("id").asLong();

        SalesOrder order = salesOrderRepository.findById(orderId).orElseThrow();
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setHoldExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        salesOrderRepository.save(order);

        mockMvc.perform(post("/sales/hold-expire")
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releasedOrders").exists());

        mockMvc.perform(get("/orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(salesToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("cancelled"));
    }

    private Map<String, Object> webOrderBody(int qty) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("variantId", variant.getId());
        line.put("unit", "pack");
        line.put("qty", qty);
        line.put("manualPrice", null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customer.getId());
        body.put("orderType", "retail");
        body.put("channel", "web");
        body.put("paymentMethod", "bank_transfer");
        body.put("fulfillmentMethod", "pickup");
        body.put("shipAddressId", null);
        body.put("shippingFee", 0);
        body.put("discountTotal", 0);
        body.put("voucherCode", null);
        body.put("note", "IT hold expire");
        body.put("lines", List.of(line));
        return body;
    }

    private void createSalesUser(String adminToken, String username) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("fullName", "Sales Hold IT");
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
