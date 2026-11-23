package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import com.hbsisms.security.TokenService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Long — IT nhập kho nguyên vật liệu 2 bước (Inter 1 dòng 46–47) qua API thật trên DB online
 * (profile {@code it-external}). {@code @Transactional}: NVL, phiếu, lô, sổ kho, user test đều rollback
 * sau mỗi test — không để rác DB chung.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it-external")
@Transactional
class RawReceiptApiIntegrationTest {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RawMaterialRepository rawMaterialRepository;
    @Autowired
    private RawMaterialLotRepository rawMaterialLotRepository;
    @Autowired
    private RawMaterialLedgerRepository rawMaterialLedgerRepository;
    @Autowired
    private GoodsReceiptRepository goodsReceiptRepository;
    @Autowired
    private RawMaterialStockService rawMaterialStockService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TokenService tokenService;

    private String warehouseToken;
    private RawMaterial ice;
    private RawMaterial salt;

    @BeforeEach
    void setUp() {
        warehouseToken = tokenFor(DefaultRoles.WAREHOUSE);
        ice = createMaterial("Đá lạnh IT", "kg", BigDecimal.ZERO);
        salt = createMaterial("Muối IT", "bao", BigDecimal.ZERO);
    }

    @Test
    void createRawReceipt_isPending_andDoesNotAddStock() throws Exception {
        JsonNode created = createPending(Map.of(ice.getId(), "10", salt.getId(), "4"));

        assertThat(created.path("status").asText()).isEqualTo("pending");
        assertThat(created.path("code").asText()).startsWith("PN-");
        assertThat(created.path("expectedDate").asText()).isEqualTo(TODAY.plusDays(2).toString());
        assertThat(created.path("lines")).hasSize(2);
        assertThat(created.path("lines").get(0).path("qtyExpected").decimalValue()).isEqualByComparingTo("10");
        assertThat(created.path("lines").get(0).path("qtyReceived").isNull()).isTrue();
        assertThat(created.path("totalAmount").isNull()).isTrue();

        assertThat(reload(ice).getQtyOnHand()).isEqualByComparingTo("0");
        assertThat(rawMaterialLotRepository.findFefo(ice.getId(), false)).isEmpty();

        long id = created.path("id").asLong();
        JsonNode pendingList = json(perform(get("/goods-receipts")
                        .param("receiptType", "raw")
                        .param("status", "pending")
                        .param("pageSize", "100"))
                .andExpect(status().isOk()));
        assertThat(pendingList.path("items").findValuesAsText("id")).contains(String.valueOf(id));
    }

    @Test
    void complete_addsStockByLot_withPriceAndExpiry_thenSecondConfirmIs409() throws Exception {
        JsonNode created = createPending(Map.of(ice.getId(), "10", salt.getId(), "4"));
        long id = created.path("id").asLong();
        long iceLine = created.path("lines").get(0).path("id").asLong();
        long saltLine = created.path("lines").get(1).path("id").asLong();

        Map<String, Object> body = completeBody(List.of(
                line(iceLine, "9.5", "DA-IT-01", TODAY.minusDays(1), TODAY.plusDays(20), "4000"),
                line(saltLine, "4", null, null, null, "15000")));
        JsonNode done = json(perform(post("/goods-receipts/" + id + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk()));

        assertThat(done.path("status").asText()).isEqualTo("completed");
        assertThat(done.path("completerName").asText()).startsWith("Kho IT");
        // 9.5 × 4.000 + 4 × 15.000 = 98.000
        assertThat(done.path("totalAmount").decimalValue()).isEqualByComparingTo("98000");
        assertThat(done.path("lines").get(0).path("lotCode").asText()).isEqualTo("DA-IT-01");
        assertThat(done.path("lines").get(1).path("lotCode").asText()).startsWith("NVL-");

        // Tồn theo lô + tổng tồn khớp nhau
        assertThat(reload(ice).getQtyOnHand()).isEqualByComparingTo("9.5");
        assertThat(reload(salt).getQtyOnHand()).isEqualByComparingTo("4");
        JsonNode lots = json(perform(get("/raw-materials/" + ice.getId() + "/lots").param("availableOnly", "true"))
                .andExpect(status().isOk()));
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).path("lotCode").asText()).isEqualTo("DA-IT-01");
        assertThat(lots.get(0).path("qtyRemaining").decimalValue()).isEqualByComparingTo("9.5");
        assertThat(lots.get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("4000");
        assertThat(lots.get(0).path("expDate").asText()).isEqualTo(TODAY.plusDays(20).toString());
        assertThat(lots.get(0).path("receiptCode").asText()).isEqualTo(created.path("code").asText());
        assertThat(rawMaterialLotRepository.sumRemaining(ice.getId())).isEqualByComparingTo("9.5");

        List<RawMaterialLedger> ledger =
                rawMaterialLedgerRepository.findByRefTypeAndRefIdOrderByIdAsc(GoodsReceiptService.REF_TYPE, id);
        assertThat(ledger).hasSize(2);
        assertThat(ledger).allSatisfy(e -> assertThat(e.getEntryType()).isEqualTo(RawLedgerEntryType.IN));

        // Xác nhận lần 2 → 409, tồn không cộng thêm
        perform(post("/goods-receipts/" + id + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
        assertThat(reload(ice).getQtyOnHand()).isEqualByComparingTo("9.5");
    }

    @Test
    void complete_missingPriceOrExpiredLot_returns400_andStaysPending() throws Exception {
        JsonNode created = createPending(Map.of(ice.getId(), "10"));
        long id = created.path("id").asLong();
        long iceLine = created.path("lines").get(0).path("id").asLong();

        perform(post("/goods-receipts/" + id + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                completeBody(List.of(line(iceLine, "10", null, null, null, null))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("đơn giá")));
        perform(post("/goods-receipts/" + id + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeBody(
                                List.of(line(iceLine, "10", null, null, TODAY.minusDays(1), "4000"))))))
                .andExpect(status().isBadRequest());

        assertThat(goodsReceiptRepository.findById(id).orElseThrow().getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(reload(ice).getQtyOnHand()).isEqualByComparingTo("0");
    }

    @Test
    void cancel_pendingReceipt_thenCompleteIs409_noStockChange() throws Exception {
        JsonNode created = createPending(Map.of(ice.getId(), "10"));
        long id = created.path("id").asLong();
        long iceLine = created.path("lines").get(0).path("id").asLong();

        JsonNode cancelled = json(perform(post("/goods-receipts/" + id + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Nhà cung cấp hết hàng\"}"))
                .andExpect(status().isOk()));
        assertThat(cancelled.path("status").asText()).isEqualTo("cancelled");
        assertThat(cancelled.path("note").asText()).contains("Hủy: Nhà cung cấp hết hàng");

        perform(post("/goods-receipts/" + id + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                completeBody(List.of(line(iceLine, "10", null, null, null, "4000"))))))
                .andExpect(status().isConflict());
        perform(post("/goods-receipts/" + id + "/cancel")).andExpect(status().isConflict());
        assertThat(reload(ice).getQtyOnHand()).isEqualByComparingTo("0");
    }

    @Test
    void salesWithoutInventoryPermission_isForbidden() throws Exception {
        JsonNode created = createPending(Map.of(ice.getId(), "10"));
        String salesToken = tokenFor(DefaultRoles.SALES);

        mockMvc.perform(post("/goods-receipts/" + created.path("id").asLong() + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/raw-materials/" + ice.getId() + "/lots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + salesToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void openingLot_existingStockWithoutLots_becomesOneLot_totalUnchanged() {
        RawMaterial legacy = createMaterial("Bao bì cũ IT", "cái", new BigDecimal("5"));

        rawMaterialStockService.ensureOpeningLots();

        List<RawMaterialLot> lots = rawMaterialLotRepository.findFefo(legacy.getId(), true);
        assertThat(lots).singleElement().satisfies(lot -> {
            assertThat(lot.getLotCode()).isEqualTo("TON-DAU");
            assertThat(lot.getQtyRemaining()).isEqualByComparingTo("5");
            assertThat(lot.getUnitPrice()).isNull();
        });
        assertThat(reload(legacy).getQtyOnHand()).isEqualByComparingTo("5");
        // Chạy lại không tạo trùng
        rawMaterialStockService.ensureOpeningLots();
        assertThat(rawMaterialLotRepository.findFefo(legacy.getId(), false)).hasSize(1);
    }

    // ---- helpers ----

    private JsonNode createPending(Map<Long, String> qtyByMaterial) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiptType", "raw");
        body.put("source", "Chợ đầu mối IT");
        body.put("note", "IT nhập NVL");
        body.put("expectedDate", TODAY.plusDays(2).toString());
        body.put("rawLines", qtyByMaterial.entrySet().stream()
                .map(e -> Map.of("rawMaterialId", e.getKey(), "qty", new BigDecimal(e.getValue())))
                .toList());
        return json(perform(post("/goods-receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()));
    }

    private static Map<String, Object> completeBody(List<Map<String, Object>> lines) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("lines", lines);
        return body;
    }

    private static Map<String, Object> line(
            long lineId, String qty, String lotCode, LocalDate mfg, LocalDate exp, String price) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("lineId", lineId);
        line.put("qtyReceived", new BigDecimal(qty));
        line.put("lotCode", lotCode);
        line.put("mfgDate", mfg == null ? null : mfg.toString());
        line.put("expDate", exp == null ? null : exp.toString());
        line.put("unitPrice", price == null ? null : new BigDecimal(price));
        return line;
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + warehouseToken));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private RawMaterial reload(RawMaterial material) {
        return rawMaterialRepository.findById(material.getId()).orElseThrow();
    }

    private RawMaterial createMaterial(String name, String unit, BigDecimal qty) {
        RawMaterial m = new RawMaterial();
        m.setCode("NVL-IT-" + SEQ.getAndIncrement() + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        m.setName(name);
        m.setUnit(unit);
        m.setQtyOnHand(qty);
        m.setActive(true);
        return rawMaterialRepository.saveAndFlush(m);
    }

    /** User riêng của test theo vai trò (rollback sau test). */
    private String tokenFor(String roleCode) {
        String suffix = SEQ.getAndIncrement() + UUID.randomUUID().toString().substring(0, 6);
        AppUser user = new AppUser();
        user.setUsername(roleCode + "_raw_it_" + suffix);
        user.setFullName((DefaultRoles.WAREHOUSE.equals(roleCode) ? "Kho IT " : "Sales IT ") + suffix);
        user.setPhone("07" + String.format("%08d", Math.abs(suffix.hashCode()) % 100_000_000));
        user.setEmail(roleCode + "_" + suffix + "@test.vn");
        user.setPasswordHash(passwordEncoder.encode("Passw0rd!"));
        user.setUserType(UserType.STAFF);
        user.setActive(true);
        user.setRole(roleRepository.findByCode(roleCode).orElseThrow());
        return tokenService.issue(userRepository.saveAndFlush(user)).value();
    }
}
