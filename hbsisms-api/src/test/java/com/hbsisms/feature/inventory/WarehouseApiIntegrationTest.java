package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Long's warehouse API IT: receive → view → move → adjust flow, concurrent stock safety, and
 * related API/DB edge cases. Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class WarehouseApiIntegrationTest {

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
    void flow_receiveViewMoveAdjust() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("FLOW");

        receive(sites.locationAId, lotCode, 20, LocalDate.now().minusDays(1), LocalDate.now().plusDays(30), "Nhập IT")
                .andExpect(status().isCreated());

        mockMvc.perform(get("/inventory/lots")
                        .param("q", lotCode)
                        .param("locationId", String.valueOf(sites.locationAId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].available").value(20))
                .andExpect(jsonPath("$.items[0].lotCode").value(lotCode));

        long stockId = findStockId(lotCode, sites.locationAId);
        assertThat(stockId).isPositive();

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(moveBody(stockId, sites.locationBId, 8))))
                .andExpect(status().isNoContent());

        Stock atA = stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(
                        variant.getId(),
                        lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow().getId(),
                        sites.locationAId)
                .orElseThrow();
        Stock atB = stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(
                        variant.getId(),
                        lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow().getId(),
                        sites.locationBId)
                .orElseThrow();
        assertThat(atA.getQtyAvailable()).isEqualTo(12);
        assertThat(atB.getQtyAvailable()).isEqualTo(8);

        assertThat(stockLedgerRepository.findAllWithDetailsOrderByCreatedAtDesc()).anySatisfy(entry -> {
            assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.MOVE);
            assertThat(entry.getQtyDelta()).isEqualTo(8);
            assertThat(entry.getToLocation().getId()).isEqualTo(sites.locationBId);
        });

        mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(adjustBody(atB.getId(), -3, "Hao IT flow"))))
                .andExpect(status().isNoContent());

        Stock afterAdjust = stockRepository.findById(atB.getId()).orElseThrow();
        assertThat(afterAdjust.getQtyAvailable()).isEqualTo(5);

        assertThat(stockLedgerRepository.findAllWithDetailsOrderByCreatedAtDesc()).anySatisfy(entry -> {
            assertThat(entry.getEntryType()).isEqualTo(LedgerEntryType.ADJUST);
            assertThat(entry.getQtyDelta()).isEqualTo(-3);
            assertThat(entry.getNote()).isEqualTo("Hao IT flow");
        });
    }

    @Test
    void view_lots_afterReceive_showsQty() throws Exception {
        Sites sites = createSites(50, 200);
        String lotCode = uniqueLot("VIEW");

        receive(sites.locationAId, lotCode, 7, null, LocalDate.now().plusDays(10), null)
                .andExpect(status().isCreated());

        mockMvc.perform(get("/inventory/lots")
                        .param("q", lotCode)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(7))
                .andExpect(jsonPath("$.items[0].sku").value("TOM-SU-05"));
    }

    @Test
    void concurrent_twoAdjusts_neverGoNegative() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("CONC-ADJ");
        receive(sites.locationAId, lotCode, 10, null, LocalDate.now().plusDays(15), null)
                .andExpect(status().isCreated());
        long stockId = findStockId(lotCode, sites.locationAId);

        List<Integer> statuses = runConcurrent(
                () -> adjustStatus(stockId, -10, "Concurrent A"),
                () -> adjustStatus(stockId, -10, "Concurrent B"));

        assertThat(statuses).contains(204);
        assertThat(statuses).allMatch(s -> s == 204 || s == 409);
        assertThat(statuses.stream().filter(s -> s == 204).count()).isEqualTo(1);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(1);

        Stock stock = stockRepository.findById(stockId).orElseThrow();
        assertThat(stock.getQtyAvailable()).isEqualTo(0);
    }

    @Test
    void concurrent_twoReceives_respectCapacity() throws Exception {
        Sites sites = createSites(10, 100);
        String lotA = uniqueLot("CONC-R1");
        String lotB = uniqueLot("CONC-R2");

        List<Integer> statuses = runConcurrent(
                () -> receiveStatus(sites.locationAId, lotA, 8),
                () -> receiveStatus(sites.locationAId, lotB, 8));

        assertThat(statuses).contains(201);
        assertThat(statuses).allMatch(s -> s == 201 || s == 409);
        long used = stockRepository.sumQtyByLocationId(sites.locationAId);
        assertThat(used).isLessThanOrEqualTo(10);
        assertThat(used).isGreaterThanOrEqualTo(8);
    }

    @Test
    void receive_exceedsLocationCapacity_returns409() throws Exception {
        Sites sites = createSites(5, 500);
        receive(sites.locationAId, uniqueLot("CAP-LOC"), 10, null, LocalDate.now().plusDays(5), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sức chứa")));
    }

    @Test
    void receive_exceedsWarehouseCapacity_returns409() throws Exception {
        Sites sites = createSites(100, 10);
        receive(sites.locationAId, uniqueLot("CAP-WH"), 15, null, LocalDate.now().plusDays(5), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Kho")));
    }

    @Test
    void receive_invalidQty_returns400() throws Exception {
        Sites sites = createSites(50, 200);
        Map<String, Object> body = receiveBody(sites.locationAId, uniqueLot("BAD-QTY"), 0, null, null, null);
        mockMvc.perform(post("/inventory/receives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void move_insufficientQty_returns409() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("MOVE-LACK");
        receive(sites.locationAId, lotCode, 2, null, LocalDate.now().plusDays(5), null)
                .andExpect(status().isCreated());
        long stockId = findStockId(lotCode, sites.locationAId);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(moveBody(stockId, sites.locationBId, 5))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Không đủ tồn kho"));
    }

    @Test
    void move_sameLocation_returns400() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("MOVE-SAME");
        receive(sites.locationAId, lotCode, 5, null, LocalDate.now().plusDays(5), null)
                .andExpect(status().isCreated());
        long stockId = findStockId(lotCode, sites.locationAId);

        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(moveBody(stockId, sites.locationAId, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vị trí đích trùng vị trí hiện tại"));
    }

    @Test
    void move_unknownStock_returns404() throws Exception {
        Sites sites = createSites(100, 500);
        mockMvc.perform(post("/inventory/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(moveBody(9_999_999L, sites.locationBId, 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy dòng tồn"));
    }

    @SafeVarargs
    private final List<Integer> runConcurrent(Callable<Integer>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch ready = new CountDownLatch(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start concurrent task");
                    }
                    return task.call();
                }));
            }
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for workers to be ready");
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    private int adjustStatus(long stockId, int qtyDelta, String note) throws Exception {
        MvcResult result = mockMvc.perform(post("/inventory/adjusts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(adjustBody(stockId, qtyDelta, note))))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private int receiveStatus(long locationId, String lotCode, int qty) throws Exception {
        MvcResult result = receive(locationId, lotCode, qty, null, LocalDate.now().plusDays(20), null).andReturn();
        return result.getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions receive(
            long locationId,
            String lotCode,
            int qty,
            LocalDate mfg,
            LocalDate exp,
            String note)
            throws Exception {
        return mockMvc.perform(post("/inventory/receives")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .content(objectMapper.writeValueAsString(receiveBody(locationId, lotCode, qty, mfg, exp, note))));
    }

    private long findStockId(String lotCode, long locationId) throws Exception {
        String body = mockMvc.perform(get("/inventory/lots")
                        .param("q", lotCode)
                        .param("locationId", String.valueOf(locationId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode items = objectMapper.readTree(body).get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isGreaterThan(0);
        return items.get(0).get("stockId").asLong();
    }

    private Sites createSites(int locationCapacity, int warehouseCapacity) {
        int n = SEQ.getAndIncrement();
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("WH-IT-" + n);
        warehouse.setName("Kho IT " + n);
        warehouse.setCapacity(warehouseCapacity);
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        Location a = new Location();
        a.setWarehouse(warehouse);
        a.setCode("A");
        a.setZone("Z");
        a.setCapacity(locationCapacity);
        a = locationRepository.save(a);

        Location b = new Location();
        b.setWarehouse(warehouse);
        b.setCode("B");
        b.setZone("Z");
        b.setCapacity(locationCapacity);
        b = locationRepository.save(b);

        return new Sites(warehouse.getId(), a.getId(), b.getId());
    }

    private static String uniqueLot(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Map<String, Object> receiveBody(
            long locationId, String lotCode, int qty, LocalDate mfg, LocalDate exp, String note) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantId", variant.getId());
        map.put("locationId", locationId);
        map.put("qty", qty);
        map.put("lotCode", lotCode);
        if (mfg != null) {
            map.put("mfgDate", mfg.toString());
        }
        if (exp != null) {
            map.put("expDate", exp.toString());
        }
        if (note != null) {
            map.put("note", note);
        }
        return map;
    }

    private static Map<String, Object> moveBody(long stockId, long toLocationId, int qty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("stockId", stockId);
        map.put("toLocationId", toLocationId);
        map.put("qty", qty);
        return map;
    }

    private static Map<String, Object> adjustBody(long stockId, int qtyDelta, String note) {
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

    private record Sites(long warehouseId, long locationAId, long locationBId) {}
}
