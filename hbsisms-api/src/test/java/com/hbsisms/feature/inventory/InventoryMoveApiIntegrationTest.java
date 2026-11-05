package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
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

/** {@code POST /inventory/moves} against real PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class InventoryMoveApiIntegrationTest {

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
    void move_updatesSourceTargetAndWritesLedger() throws Exception {
        Fixture fx = createFixture(10);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.sourceStockId, fx.targetLocationId, 4))))
                .andExpect(status().isNoContent());

        Stock source = stockRepository.findById(fx.sourceStockId).orElseThrow();
        Stock target = stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variant.getId(), fx.lotId, fx.targetLocationId)
                .orElseThrow();
        assertThat(source.getQtyAvailable()).isEqualTo(6);
        assertThat(target.getQtyAvailable()).isEqualTo(4);

        assertThat(stockLedgerRepository.findAllWithDetailsOrderByCreatedAtDesc()).anySatisfy(entry -> {
            assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.MOVE);
            assertThat(entry.getQtyDelta()).isEqualTo(4);
            assertThat(entry.getToLocation().getId()).isEqualTo(fx.targetLocationId);
            assertThat(entry.getNote()).isEqualTo("Chuyển vị trí");
        });
    }

    @Test
    void move_unknownStock_returns404() throws Exception {
        Fixture fx = createFixture(5);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(9_999_999L, fx.targetLocationId, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy dòng tồn"));
    }

    @Test
    void move_sameLocation_returns400() throws Exception {
        Fixture fx = createFixture(5);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.sourceStockId, fx.sourceLocationId, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vị trí đích trùng vị trí hiện tại"));
    }

    @Test
    void move_qtyExceedsAvailable_returns409() throws Exception {
        Fixture fx = createFixture(3);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body(fx.sourceStockId, fx.targetLocationId, 4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không đủ tồn kho"));
    }

    private Fixture createFixture(int available) {
        int n = SEQ.getAndIncrement();
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("WH-MV-" + n);
        warehouse.setName("Kho move " + n);
        warehouse.setCapacity(500);
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        Location sourceLoc = new Location();
        sourceLoc.setWarehouse(warehouse);
        sourceLoc.setCode("SRC");
        sourceLoc.setZone("Z1");
        sourceLoc.setCapacity(100);
        sourceLoc = locationRepository.save(sourceLoc);

        Location targetLoc = new Location();
        targetLoc.setWarehouse(warehouse);
        targetLoc.setCode("DST");
        targetLoc.setZone("Z2");
        targetLoc.setCapacity(100);
        targetLoc = locationRepository.save(targetLoc);

        Lot lot = new Lot();
        lot.setLotCode("LOT-MV-" + n + "-" + UUID.randomUUID().toString().substring(0, 8));
        lot.setVariant(variant);
        lot.setMfgDate(LocalDate.now().minusDays(2));
        lot.setExpDate(LocalDate.now().plusDays(30));
        lot.setReceivedAt(Instant.now());
        lot = lotRepository.save(lot);

        Stock stock = new Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(sourceLoc);
        stock.setQtyAvailable(available);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        stock = stockRepository.save(stock);

        return new Fixture(stock.getId(), lot.getId(), sourceLoc.getId(), targetLoc.getId());
    }

    private static Map<String, Object> body(long stockId, long toLocationId, int qty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stockId", stockId);
        map.put("toLocationId", toLocationId);
        map.put("qty", qty);
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

    private record Fixture(long sourceStockId, long lotId, long sourceLocationId, long targetLocationId) {
    }
}
