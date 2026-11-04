package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.system.AuditLog;
import com.hbsisms.feature.system.AuditLogRepository;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** {@code POST /inventory/adjusts} against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InventoryAdjustApiIntegrationTest {

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
    private WarehouseRepository warehouseRepository;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private LotRepository lotRepository;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockLedgerRepository stockLedgerRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private ProductVariantRepository productVariantRepository;

    private String adminToken;
    private ProductVariant variant;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", ADMIN_PASSWORD);
        variant = productVariantRepository.findAll().stream()
                .filter(v -> "TOM-SU-05".equals(v.getSku()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Demo variant TOM-SU-05 must be seeded"));
    }

    @Test
    void adjust_decreasesStockWritesLedgerAndAudit() throws Exception {
        Fixture fx = createFixture(10);

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                body(fx.stockId, -2, "Hao hụt kiểm kê tay"))))
                .andExpect(status().isNoContent());

        Stock stock = stockRepository.findById(fx.stockId).orElseThrow();
        assertThat(stock.getQtyAvailable()).isEqualTo(8);

        assertThat(stockLedgerRepository.findAllWithDetailsOrderByCreatedAtDesc()).anySatisfy(entry -> {
            assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.ADJUST);
            assertThat(entry.getQtyDelta()).isEqualTo(-2);
            assertThat(entry.getNote()).isEqualTo("Hao hụt kiểm kê tay");
        });

        assertThat(auditLogRepository.findAll()).anySatisfy((AuditLog log) -> {
            assertThat(log.getAction()).isEqualTo("adjust");
            assertThat(log.getEntityType()).isEqualTo("stock");
            assertThat(log.getEntityId()).isEqualTo(fx.stockId);
            assertThat(log.getDetail()).containsEntry("qtyDelta", -2);
        });
    }

    @Test
    void adjust_increasesStock() throws Exception {
        Fixture fx = createFixture(5);

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.stockId, 3, "Bù thiếu đếm"))))
                .andExpect(status().isNoContent());

        Stock stock = stockRepository.findById(fx.stockId).orElseThrow();
        assertThat(stock.getQtyAvailable()).isEqualTo(8);
    }

    @Test
    void adjust_blankNote_returns400() throws Exception {
        Fixture fx = createFixture(5);

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.stockId, -1, "  "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adjust_zeroDelta_returns400() throws Exception {
        Fixture fx = createFixture(5);

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.stockId, 0, "Không đổi"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số lượng điều chỉnh phải khác 0"));
    }

    @Test
    void adjust_unknownStock_returns404() throws Exception {
        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(9_999_999L, -1, "Sai id"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy dòng tồn"));
    }

    @Test
    void adjust_exceedsAvailable_returns409() throws Exception {
        Fixture fx = createFixture(2);

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.stockId, -5, "Trừ quá"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không đủ tồn kho"));
    }

    private Fixture createFixture(int available) {
        int n = SEQ.getAndIncrement();
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("WH-ADJ-" + n);
        warehouse.setName("Kho adjust " + n);
        warehouse.setCapacity(500);
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        Location location = new Location();
        location.setWarehouse(warehouse);
        location.setCode("LOC");
        location.setZone("Z");
        location.setCapacity(100);
        location = locationRepository.save(location);

        Lot lot = new Lot();
        lot.setLotCode("LOT-ADJ-" + n + "-" + UUID.randomUUID().toString().substring(0, 8));
        lot.setVariant(variant);
        lot.setMfgDate(LocalDate.now().minusDays(1));
        lot.setExpDate(LocalDate.now().plusDays(20));
        lot.setReceivedAt(Instant.now());
        lot = lotRepository.save(lot);

        Stock stock = new Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(available);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        stock = stockRepository.save(stock);

        return new Fixture(stock.getId());
    }

    private static Map<String, Object> body(long stockId, int qtyDelta, String note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stockId", stockId);
        map.put("qtyDelta", qtyDelta);
        map.put("note", note);
        return map;
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

    private record Fixture(long stockId) {
    }
}
