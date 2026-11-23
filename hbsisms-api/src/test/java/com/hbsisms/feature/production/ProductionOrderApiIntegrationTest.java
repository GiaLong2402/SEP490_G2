package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.catalog.UnitOfMeasure;
import com.hbsisms.feature.catalog.UnitOfMeasureRepository;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import com.hbsisms.security.TokenService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Duy — Inter 1 task 48–49: lệnh SX + duyệt sinh phiếu xuất NVL.
 * Testcontainers Postgres tạm — không đụng Neon / DB chung nhóm.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ProductionOrderApiIntegrationTest {

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
    private ProductRepository productRepository;
    @Autowired
    private ProductBomLineRepository bomLineRepository;
    @Autowired
    private RawMaterialRepository rawMaterialRepository;
    @Autowired
    private RawMaterialLotRepository rawMaterialLotRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private UnitOfMeasureRepository unitOfMeasureRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private MaterialIssueRepository materialIssueRepository;

    private String adminToken;
    private String salesToken;
    private UnitOfMeasure kg;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
        salesToken = tokenForSales();
        kg = unitOfMeasureRepository.findAll().stream()
                .filter(u -> "kg".equalsIgnoreCase(u.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    UnitOfMeasure u = new UnitOfMeasure();
                    u.setCode("kg");
                    u.setName("Kilogram");
                    u.setActive(true);
                    return unitOfMeasureRepository.save(u);
                });
    }

    @Test
    void create_thenGet_showsMaterialLines() throws Exception {
        Fixture fx = fixture(new BigDecimal("100"), new BigDecimal("0.4"));

        MvcResult created = mockMvc.perform(post("/production-orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody(fx.productId, "100"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("LSX-")))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.allMaterialsSufficient").value(true))
                .andExpect(jsonPath("$.materials[0].stockStatus").value("Đủ"))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/production-orders/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materials.length()").value(1))
                .andExpect(jsonPath("$.materials[0].requiredQty").value(40.0));
    }

    @Test
    void approve_whenSufficient_createsPendingMaterialIssue() throws Exception {
        Fixture fx = fixture(new BigDecimal("100"), new BigDecimal("0.4"));

        long orderId = objectMapper
                .readTree(mockMvc.perform(post("/production-orders")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createBody(fx.productId, "10"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asLong();

        JsonNode approved = objectMapper.readTree(mockMvc.perform(post("/production-orders/" + orderId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.materialIssueCode").value(org.hamcrest.Matchers.startsWith("PX-")))
                .andReturn()
                .getResponse()
                .getContentAsString());

        long issueId = approved.get("materialIssueId").asLong();
        mockMvc.perform(get("/material-issues/" + issueId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.productionOrderId").value(orderId))
                .andExpect(jsonPath("$.lines.length()").value(1));

        assertThat(materialIssueRepository.findById(issueId)).isPresent();
    }

    @Test
    void approve_whenShortage_returns409() throws Exception {
        Fixture fx = fixture(new BigDecimal("1"), new BigDecimal("0.4"));

        long orderId = objectMapper
                .readTree(mockMvc.perform(post("/production-orders")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createBody(fx.productId, "100"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.allMaterialsSufficient").value(false))
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asLong();

        mockMvc.perform(post("/production-orders/" + orderId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Không đủ nguyên liệu")));
    }

    @Test
    void create_withoutInventoryAuthority_returns403() throws Exception {
        Fixture fx = fixture(new BigDecimal("100"), new BigDecimal("0.4"));

        mockMvc.perform(post("/production-orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(salesToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createBody(fx.productId, "1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmIssue_thenComplete_createsPendingFinishedReceipt() throws Exception {
        Fixture fx = fixture(new BigDecimal("100"), new BigDecimal("0.4"));

        long orderId = objectMapper
                .readTree(mockMvc.perform(post("/production-orders")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createBody(fx.productId, "10"))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get("id")
                .asLong();

        JsonNode approved = objectMapper.readTree(mockMvc.perform(post("/production-orders/" + orderId + "/approve")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        long issueId = approved.get("materialIssueId").asLong();

        mockMvc.perform(post("/material-issues/" + issueId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        mockMvc.perform(get("/production-orders/" + orderId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_progress"));

        Map<String, Object> completeBody = new LinkedHashMap<>();
        completeBody.put("actualQty", new BigDecimal("9.5"));
        completeBody.put("actualProductionCost", new BigDecimal("120000"));

        mockMvc.perform(post("/production-orders/" + orderId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("awaiting_receipt"))
                .andExpect(jsonPath("$.actualQty").value(9.5))
                .andExpect(jsonPath("$.finishedReceiptCode").value(org.hamcrest.Matchers.startsWith("PN-")));

        RawMaterial after = rawMaterialRepository.findById(fx.rawMaterialId).orElseThrow();
        assertThat(after.getQtyOnHand()).isEqualByComparingTo("96"); // 100 - 4 (10*0.4)
    }

    private Fixture fixture(BigDecimal onHand, BigDecimal bomQty) {
        String suffix = SEQ.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 6);

        Product product = new Product();
        product.setCode("TP-LSX-" + suffix);
        product.setName("Thành phẩm LSX " + suffix);
        product.setActive(true);
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("SKU-LSX-" + suffix);
        variant.setPackLabel("Khay");
        variant.setListPrice(new BigDecimal("50000"));
        variant.setActive(true);
        productVariantRepository.save(variant);

        RawMaterial raw = new RawMaterial();
        raw.setCode("NL-LSX-" + suffix);
        raw.setName("NVL LSX " + suffix);
        raw.setUnit("kg");
        raw.setUnitRef(kg);
        raw.setQtyOnHand(onHand);
        raw.setActive(true);
        raw = rawMaterialRepository.save(raw);

        RawMaterialLot lot = new RawMaterialLot();
        lot.setRawMaterial(raw);
        lot.setLotCode("LOT-" + suffix);
        lot.setQtyReceived(onHand);
        lot.setQtyRemaining(onHand);
        lot.setUnitPrice(new BigDecimal("1000"));
        rawMaterialLotRepository.save(lot);

        ProductBomLine line = new ProductBomLine();
        line.setProduct(product);
        line.setRawMaterial(raw);
        line.setQty(bomQty);
        line.setWastePercent(BigDecimal.ZERO);
        line.setUnit(kg);
        line.setSortOrder(0);
        bomLineRepository.save(line);

        return new Fixture(product.getId(), raw.getId());
    }

    private Map<String, Object> createBody(Long productId, String plannedQty) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", productId);
        body.put("plannedQty", new BigDecimal(plannedQty));
        body.put("note", "IT lệnh SX");
        body.put("estimatedProductionCost", new BigDecimal("100000"));
        return body;
    }

    private String login(String username, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private String tokenForSales() {
        String suffix = SEQ.incrementAndGet() + UUID.randomUUID().toString().substring(0, 6);
        AppUser user = new AppUser();
        user.setUsername("sales_lsx_" + suffix);
        user.setEmail("sales_lsx_" + suffix + "@test.vn");
        user.setPhone("08" + String.format("%08d", Math.abs(suffix.hashCode()) % 100_000_000));
        user.setPasswordHash(passwordEncoder.encode("Sales@12345"));
        user.setFullName("Sales LSX");
        user.setUserType(UserType.STAFF);
        user.setRole(roleRepository.findByCode(DefaultRoles.SALES).orElseThrow());
        user.setActive(true);
        return tokenService.issue(userRepository.saveAndFlush(user)).value();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record Fixture(Long productId, Long rawMaterialId) {}
}
