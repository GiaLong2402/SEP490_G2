package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.catalog.VolumeTier;
import com.hbsisms.feature.catalog.VolumeTierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Khôi — IT API địa chỉ + đề xuất/duyệt giá B2B + PriceResolver (task 24).
 * Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CustomerAddressPricingApiIntegrationTest {

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
    @Autowired
    private CustomerAddressRepository addressRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private CustomerPriceRepository customerPriceRepository;
    @Autowired
    private VolumeTierRepository volumeTierRepository;
    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;

    private String adminToken;
    private ProductVariant variant;
    private Customer b2c;
    private Customer b2b;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
        Long variantId = productVariantRepository.findAll().stream()
                .filter(v -> "TOM-SU-05".equals(v.getSku()))
                .map(ProductVariant::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Demo variant TOM-SU-05 must be seeded"));
        variant = productVariantRepository
                .findWithProductById(variantId)
                .orElseThrow(() -> new IllegalStateException("Demo variant TOM-SU-05 must be seeded"));
        b2c = requireCustomer("KH-001");
        b2b = requireCustomer("KH-B002"); // sạch hạn mức, tránh đụng địa chỉ seed của KH-B001
    }

    @Test
    void address_createDefault_clearsPreviousAndMarksInRadius() throws Exception {
        MvcResult first = mockMvc.perform(post("/customers/" + b2c.getId() + "/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                addressBody("shipping", "A", "0901000001", "1 IT A", "3.0", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.inDeliveryRadius").value(true))
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/customers/" + b2c.getId() + "/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                addressBody("shipping", "B", "0901000002", "2 IT B", "5.0", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.inDeliveryRadius").value(true))
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        assertThat(addressRepository.findById(firstId).orElseThrow().isDefaultAddress()).isFalse();
        assertThat(addressRepository.findById(secondId).orElseThrow().isDefaultAddress()).isTrue();
    }

    @Test
    void address_create_outsideRadius_setsFlagFalse() throws Exception {
        mockMvc.perform(post("/customers/" + b2c.getId() + "/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                addressBody("shipping", "Far", "0901000003", "Xa", "8.01", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inDeliveryRadius").value(false))
                .andExpect(jsonPath("$.isDefault").value(false));
    }

    @Test
    void address_updateAndDelete_roundTrip() throws Exception {
        MvcResult created = mockMvc.perform(post("/customers/" + b2c.getId() + "/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                addressBody("billing", "Old", "0901000004", "Line old", "2.0", false))))
                .andExpect(status().isCreated())
                .andReturn();
        long addressId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/customers/" + b2c.getId() + "/addresses/" + addressId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                addressBody("shipping", "New", "0901000005", "Line new", "1.0", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("New"))
                .andExpect(jsonPath("$.line1").value("Line new"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.kind").value("shipping"));

        mockMvc.perform(delete("/customers/" + b2c.getId() + "/addresses/" + addressId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/customers/" + b2c.getId() + "/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    JsonNode arr = objectMapper.readTree(result.getResponse().getContentAsString());
                    for (JsonNode node : arr) {
                        assertThat(node.get("id").asLong()).isNotEqualTo(addressId);
                    }
                });
    }

    @Test
    void address_unknownCustomer_returns404() throws Exception {
        mockMvc.perform(get("/customers/999999/addresses")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void pricing_guest_usesListPrice() throws Exception {
        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(null, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("list"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(variant.getListPrice().doubleValue()));
    }

    @Test
    void pricing_withVolumeTier_usesTierWhenNoSpecial() throws Exception {
        ensureTier(10, new BigDecimal("150000.00"));

        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(b2b.getId(), 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("tier"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(150000.0));
    }

    @Test
    void price_proposeB2c_returns400() throws Exception {
        mockMvc.perform(post("/customers/" + b2c.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(proposeBody(new BigDecimal("120000.00"), null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void price_proposeApprove_resolveUsesCustomerPrice() throws Exception {
        Customer customer = newB2b("IT-APR");
        BigDecimal special = new BigDecimal("111000.00");

        mockMvc.perform(post("/customers/" + customer.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(proposeBody(special, null, null))))
                .andExpect(status().isNoContent());

        // Chưa duyệt → vẫn list/tier, không CUSTOMER
        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(customer.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("list"));

        CustomerPrice pending = customerPriceRepository
                .findByCustomerIdAndVariantId(customer.getId(), variant.getId())
                .orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(ApprovalStatus.PENDING);

        long taskId = pendingApprovalId(CustomerPriceService.REF_TYPE_CUSTOMER_PRICE, pending.getId());
        decide(taskId, "approved", "OK giá riêng");

        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(customer.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("customer"))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(111000.0));
    }

    @Test
    void price_reject_keepsListPrice() throws Exception {
        Customer customer = newB2b("IT-REJ");

        mockMvc.perform(post("/customers/" + customer.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                proposeBody(new BigDecimal("99000.00"), null, null))))
                .andExpect(status().isNoContent());

        CustomerPrice pending = customerPriceRepository
                .findByCustomerIdAndVariantId(customer.getId(), variant.getId())
                .orElseThrow();
        decide(pendingApprovalId(CustomerPriceService.REF_TYPE_CUSTOMER_PRICE, pending.getId()), "rejected", "Cao");

        assertThat(customerPriceRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ApprovalStatus.REJECTED);

        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(customer.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("list"));
    }

    @Test
    void price_effectiveWindow_outsideDoesNotApply() throws Exception {
        Customer expiredCust = newB2b("IT-EXP");
        mockMvc.perform(post("/customers/" + expiredCust.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(proposeBody(
                                new BigDecimal("88000.00"),
                                LocalDate.now().minusDays(10),
                                LocalDate.now().minusDays(1)))))
                .andExpect(status().isNoContent());
        CustomerPrice expired = customerPriceRepository
                .findByCustomerIdAndVariantId(expiredCust.getId(), variant.getId())
                .orElseThrow();
        decide(
                pendingApprovalId(CustomerPriceService.REF_TYPE_CUSTOMER_PRICE, expired.getId()),
                "approved",
                "đã hết hạn");

        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(expiredCust.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("list"));

        Customer futureCust = newB2b("IT-FUT");
        mockMvc.perform(post("/customers/" + futureCust.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(proposeBody(
                                new BigDecimal("87000.00"),
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(30)))))
                .andExpect(status().isNoContent());
        CustomerPrice future = customerPriceRepository
                .findByCustomerIdAndVariantId(futureCust.getId(), variant.getId())
                .orElseThrow();
        decide(
                pendingApprovalId(CustomerPriceService.REF_TYPE_CUSTOMER_PRICE, future.getId()),
                "approved",
                "chưa tới ngày");

        mockMvc.perform(post("/pricing/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(resolveBody(futureCust.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].priceBasis").value("list"));
    }

    @Test
    void price_propose_invalidWindowOrDuplicatePending_returns4xx() throws Exception {
        Customer customer = newB2b("IT-VAL");

        mockMvc.perform(post("/customers/" + customer.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(proposeBody(
                                new BigDecimal("100000.00"),
                                LocalDate.now().plusDays(5),
                                LocalDate.now()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/customers/" + customer.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                proposeBody(new BigDecimal("100000.00"), null, null))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/customers/" + customer.getId() + "/prices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                proposeBody(new BigDecimal("95000.00"), null, null))))
                .andExpect(status().isConflict());
    }

    private void ensureTier(int minQtyKhay, BigDecimal unitPrice) {
        boolean exists = volumeTierRepository
                .findByVariant_IdOrderByMinQtyKhayAsc(variant.getId())
                .stream()
                .anyMatch(t -> t.getMinQtyKhay() == minQtyKhay);
        if (exists) {
            return;
        }
        VolumeTier tier = new VolumeTier();
        tier.setVariant(variant);
        tier.setMinQtyKhay(minQtyKhay);
        tier.setUnitPrice(unitPrice);
        volumeTierRepository.save(tier);
    }

    private Customer newB2b(String prefix) {
        int n = SEQ.getAndIncrement();
        Customer c = new Customer();
        c.setCode("KH-IT-" + prefix + "-" + n);
        c.setCustomerType(CustomerType.B2B);
        c.setName("IT B2B " + prefix + " " + n);
        c.setPhone("09" + String.format("%08d", n));
        c.setTaxCode(String.format("%010d", 2000000000L + n));
        c.setCreditLimit(new BigDecimal("10000000"));
        c.setNetDays((short) 15);
        c.setCurrentDebt(BigDecimal.ZERO);
        c.setActive(true);
        return customerRepository.save(c);
    }

    private Customer requireCustomer(String code) {
        return customerRepository.findAll().stream()
                .filter(c -> code.equals(c.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seeded customer " + code + " missing"));
    }

    private long pendingApprovalId(String refType, long refId) {
        return approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(refType, refId, ApprovalStatus.PENDING)
                .filter(t -> t.getApprovalType() == ApprovalType.PRICE_LIST)
                .orElseThrow()
                .getId();
    }

    private void decide(long taskId, String decision, String note) throws Exception {
        mockMvc.perform(post("/approvals/" + taskId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("decision", decision, "note", note))))
                .andExpect(status().isNoContent());
    }

    private Map<String, Object> resolveBody(Long customerId, int qty) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("paymentMethod", "cash");
        body.put("shippingFee", 0);
        body.put("discountTotal", 0);
        body.put(
                "lines",
                List.of(Map.of(
                        "variantId", variant.getId(),
                        "unit", "pack",
                        "qty", qty)));
        return body;
    }

    private Map<String, Object> proposeBody(BigDecimal unitPrice, LocalDate from, LocalDate to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variantId", variant.getId());
        body.put("unitPrice", unitPrice);
        body.put("effectiveFrom", from == null ? null : from.toString());
        body.put("effectiveTo", to == null ? null : to.toString());
        return body;
    }

    private static Map<String, Object> addressBody(
            String kind, String recipient, String phone, String line1, String distanceKm, boolean isDefault) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("recipientName", recipient);
        body.put("phone", phone);
        body.put("line1", line1);
        body.put("ward", "Ward IT");
        body.put("district", "District IT");
        body.put("province", "HP");
        body.put("distanceKm", new BigDecimal(distanceKm));
        body.put("isDefault", isDefault);
        return body;
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
