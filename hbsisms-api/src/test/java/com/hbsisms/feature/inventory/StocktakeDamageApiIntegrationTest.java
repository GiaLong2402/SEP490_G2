package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Khôi — IT API stocktake + damage-reports (task 40). Requires Docker (Testcontainers Postgres).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class StocktakeDamageApiIntegrationTest {

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
    private ProductVariantRepository productVariantRepository;
    @Autowired
    private ApprovalTaskRepository approvalTaskRepository;
    @Autowired
    private DamageReportRepository damageReportRepository;

    private String adminToken;
    private ProductVariant variant;

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
    }

    @Test
    void stocktake_noDiff_completesDoneWithoutApproval() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("ST-OK");
        receive(sites.locationId, lotCode, 12).andExpect(status().isCreated());

        long stocktakeId = createStocktake(sites.zone);
        countAllLinesAsSystem(stocktakeId);

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsApproval").value(false));

        mockMvc.perform(get("/stocktakes/" + stocktakeId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        Stock stock = requireStock(lotCode, sites.locationId);
        assertThat(stock.getQtyAvailable()).isEqualTo(12);
        assertThat(approvalTaskRepository
                        .findByRefTypeAndRefIdAndStatus(
                                StocktakeService.REF_TYPE, stocktakeId, ApprovalStatus.PENDING))
                .isEmpty();
    }

    @Test
    void stocktake_withDiff_approve_adjustsStock() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("ST-DIFF");
        receive(sites.locationId, lotCode, 20).andExpect(status().isCreated());

        long stocktakeId = createStocktake(sites.zone);
        JsonNode lines = getStocktakeLines(stocktakeId);
        assertThat(lines.size()).isGreaterThan(0);
        long lineId = lines.get(0).get("id").asLong();
        int systemQty = lines.get(0).get("systemQty").asInt();
        int counted = systemQty - 3;

        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(countsBody(lineId, counted))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsApproval").value(true));

        long taskId = pendingApprovalId(StocktakeService.REF_TYPE, stocktakeId, ApprovalType.STOCKTAKE);
        decide(taskId, "approved", "OK kiểm kê");

        mockMvc.perform(get("/stocktakes/" + stocktakeId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        Stock stock = requireStock(lotCode, sites.locationId);
        assertThat(stock.getQtyAvailable()).isEqualTo(counted);
    }

    @Test
    void stocktake_withDiff_reject_keepsCountingWithoutAdjust() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("ST-REJ");
        receive(sites.locationId, lotCode, 15).andExpect(status().isCreated());

        long stocktakeId = createStocktake(sites.zone);
        JsonNode line = getStocktakeLines(stocktakeId).get(0);
        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                countsBody(line.get("id").asLong(), line.get("systemQty").asInt() + 2))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsApproval").value(true));

        long taskId = pendingApprovalId(StocktakeService.REF_TYPE, stocktakeId, ApprovalType.STOCKTAKE);
        decide(taskId, "rejected", "Đếm lại");

        mockMvc.perform(get("/stocktakes/" + stocktakeId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("counting"));

        Stock stock = requireStock(lotCode, sites.locationId);
        assertThat(stock.getQtyAvailable()).isEqualTo(15);
    }

    @Test
    void stocktake_cancel_marksCancelled() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("ST-CAN");
        receive(sites.locationId, lotCode, 5).andExpect(status().isCreated());
        long stocktakeId = createStocktake(sites.zone);

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/stocktakes/" + stocktakeId).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));
    }

    @Test
    void damage_createDoesNotDeduct_approveDoes() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-OK");
        receive(sites.locationId, lotCode, 10).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();

        MvcResult created = mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(variant.getId(), lot.getId(), sites.locationId, 4, "Vỡ bao"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.qty").value(4))
                .andReturn();

        long reportId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(requireStock(lotCode, sites.locationId).getQtyAvailable()).isEqualTo(10);

        long taskId = pendingApprovalId(DamageReportService.REF_TYPE, reportId, ApprovalType.DAMAGE_REPORT);
        decide(taskId, "approved", "Duyệt hỏng");

        DamageReport report = damageReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(requireStock(lotCode, sites.locationId).getQtyAvailable()).isEqualTo(6);
    }

    @Test
    void damage_rejectDoesNotTouchStock() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-REJ");
        receive(sites.locationId, lotCode, 8).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();

        MvcResult created = mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(variant.getId(), lot.getId(), sites.locationId, 3, "Nghi ngờ"))))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        long taskId = pendingApprovalId(DamageReportService.REF_TYPE, reportId, ApprovalType.DAMAGE_REPORT);
        decide(taskId, "rejected", "Thiếu bằng chứng");

        DamageReport report = damageReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(requireStock(lotCode, sites.locationId).getQtyAvailable()).isEqualTo(8);
    }

    @Test
    void damage_create_rejectsWhenQtyExceedsOnHand() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-OVER");
        receive(sites.locationId, lotCode, 2).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();

        mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(variant.getId(), lot.getId(), sites.locationId, 9, "Quá tồn"))))
                .andExpect(status().isConflict());
    }

    @Test
    void stocktake_create_emptyZone_returns409() throws Exception {
        Sites empty = createSites(50, 200);
        mockMvc.perform(post("/stocktakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                Map.of("zone", empty.zone, "note", "zone trống"))))
                .andExpect(status().isConflict());
    }

    @Test
    void stocktake_complete_uncountedLines_returns409() throws Exception {
        Sites sites = createSites(100, 500);
        receive(sites.locationId, uniqueLot("ST-UNC"), 6).andExpect(status().isCreated());
        long stocktakeId = createStocktake(sites.zone);

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void stocktake_saveCounts_negativeQty_returns400() throws Exception {
        Sites sites = createSites(100, 500);
        receive(sites.locationId, uniqueLot("ST-NEG"), 4).andExpect(status().isCreated());
        long stocktakeId = createStocktake(sites.zone);
        long lineId = getStocktakeLines(stocktakeId).get(0).get("id").asLong();

        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(countsBody(lineId, -1))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stocktake_saveCounts_whilePendingApproval_returns409() throws Exception {
        Sites sites = createSites(100, 500);
        receive(sites.locationId, uniqueLot("ST-PEND"), 10).andExpect(status().isCreated());
        long stocktakeId = createStocktake(sites.zone);
        JsonNode line = getStocktakeLines(stocktakeId).get(0);
        long lineId = line.get("id").asLong();

        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                countsBody(lineId, line.get("systemQty").asInt() - 1))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/stocktakes/" + stocktakeId + "/complete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.needsApproval").value(true));

        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(countsBody(lineId, 0))))
                .andExpect(status().isConflict());
    }

    @Test
    void damage_create_blankReason_returns400() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-REASON");
        receive(sites.locationId, lotCode, 5).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();

        mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(variant.getId(), lot.getId(), sites.locationId, 1, "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void damage_create_lotVariantMismatch_returns400() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-MIS");
        receive(sites.locationId, lotCode, 5).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();
        ProductVariant other = extraVariant();

        mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(other.getId(), lot.getId(), sites.locationId, 1, "Sai quy cách"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void damage_decideAgain_returns409() throws Exception {
        Sites sites = createSites(100, 500);
        String lotCode = uniqueLot("DMG-2X");
        receive(sites.locationId, lotCode, 5).andExpect(status().isCreated());
        Lot lot = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow();

        MvcResult created = mockMvc.perform(post("/damage-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                damageBody(variant.getId(), lot.getId(), sites.locationId, 1, "Duyệt 2 lần"))))
                .andExpect(status().isCreated())
                .andReturn();
        long reportId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        long taskId = pendingApprovalId(DamageReportService.REF_TYPE, reportId, ApprovalType.DAMAGE_REPORT);

        decide(taskId, "approved", "Lần 1");
        mockMvc.perform(post("/approvals/" + taskId + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(
                                Map.of("decision", "rejected", "note", "Lần 2"))))
                .andExpect(status().isConflict());
        assertThat(requireStock(lotCode, sites.locationId).getQtyAvailable()).isEqualTo(4);
    }

    private ProductVariant extraVariant() {
        ProductVariant other = new ProductVariant();
        other.setProduct(variant.getProduct());
        other.setSku("IT-OTHER-" + SEQ.getAndIncrement());
        other.setPackLabel("Khay IT khác");
        other.setListPrice(variant.getListPrice());
        other.setPackPerCase(variant.getPackPerCase());
        other.setActive(true);
        return productVariantRepository.save(other);
    }

    private long createStocktake(String zone) throws Exception {
        MvcResult result = mockMvc.perform(post("/stocktakes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("zone", zone, "note", "IT stocktake"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("counting"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void countAllLinesAsSystem(long stocktakeId) throws Exception {
        JsonNode lines = getStocktakeLines(stocktakeId);
        List<Map<String, Object>> counts = new ArrayList<>();
        for (JsonNode line : lines) {
            counts.add(Map.of("lineId", line.get("id").asLong(), "countedQty", line.get("systemQty").asInt()));
        }
        mockMvc.perform(put("/stocktakes/" + stocktakeId + "/lines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .content(objectMapper.writeValueAsString(Map.of("counts", counts))))
                .andExpect(status().isNoContent());
    }

    private JsonNode getStocktakeLines(long stocktakeId) throws Exception {
        String body = mockMvc.perform(get("/stocktakes/" + stocktakeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).get("lines");
    }

    private long pendingApprovalId(String refType, long refId, ApprovalType type) {
        return approvalTaskRepository
                .findByRefTypeAndRefIdAndStatus(refType, refId, ApprovalStatus.PENDING)
                .filter(t -> t.getApprovalType() == type)
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

    private org.springframework.test.web.servlet.ResultActions receive(long locationId, String lotCode, int qty)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("variantId", variant.getId());
        body.put("locationId", locationId);
        body.put("qty", qty);
        body.put("lotCode", lotCode);
        body.put("expDate", LocalDate.now().plusDays(30).toString());
        return mockMvc.perform(post("/inventory/receives")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .content(objectMapper.writeValueAsString(body)));
    }

    private Stock requireStock(String lotCode, long locationId) {
        long lotId = lotRepository.findByLotCodeIgnoreCase(lotCode).orElseThrow().getId();
        return stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variant.getId(), lotId, locationId)
                .orElseThrow();
    }

    private Sites createSites(int locationCapacity, int warehouseCapacity) {
        int n = SEQ.getAndIncrement();
        String zone = "ST-Z-" + n;
        Warehouse warehouse = new Warehouse();
        warehouse.setCode("WH-ST-" + n);
        warehouse.setName("Kho ST " + n);
        warehouse.setCapacity(warehouseCapacity);
        warehouse.setActive(true);
        warehouse = warehouseRepository.save(warehouse);

        Location location = new Location();
        location.setWarehouse(warehouse);
        location.setCode("A");
        location.setZone(zone);
        location.setCapacity(locationCapacity);
        location = locationRepository.save(location);
        return new Sites(warehouse.getId(), location.getId(), zone);
    }

    private static Map<String, Object> countsBody(long lineId, int countedQty) {
        return Map.of("counts", List.of(Map.of("lineId", lineId, "countedQty", countedQty)));
    }

    private static Map<String, Object> damageBody(
            long variantId, long lotId, long locationId, int qty, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variantId", variantId);
        map.put("lotId", lotId);
        map.put("locationId", locationId);
        map.put("qty", qty);
        map.put("reason", reason);
        map.put("note", null);
        return map;
    }

    private static String uniqueLot(String prefix) {
        return prefix + "-" + SEQ.getAndIncrement() + "-" + UUID.randomUUID().toString().substring(0, 8);
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

    private record Sites(long warehouseId, long locationId, String zone) {}
}
