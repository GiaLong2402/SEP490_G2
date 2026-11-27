package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.catalog.UnitOfMeasure;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BomExplosionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-24T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ProductBomLineRepository bomLineRepository;
    @Mock
    private RawMaterialLotRepository rawMaterialLotRepository;
    @Mock
    private com.hbsisms.feature.costing.CostingService costingService;

    private BomExplosionService service;

    @BeforeEach
    void setUp() {
        service = new BomExplosionService(bomLineRepository, rawMaterialLotRepository, costingService, CLOCK);
        org.mockito.Mockito.lenient()
                .when(costingService.currentRawAvgUnitCost(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void explode_appliesWasteAndComparesUsableOnHand() {
        RawMaterial raw = raw(5L, "NL-BOT", new BigDecimal("200"));
        ProductBomLine bom = bomLine(raw, null, new BigDecimal("0.4"), new BigDecimal("5"));
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(bom));
        when(rawMaterialLotRepository.sumUsableRemaining(5L, TODAY)).thenReturn(new BigDecimal("85"));
        when(rawMaterialLotRepository.sumExpiredRemaining(5L, TODAY)).thenReturn(new BigDecimal("115"));
        when(rawMaterialLotRepository.findFefoUsable(eq(5L), eq(TODAY)))
                .thenReturn(List.of(lot(raw, "10", "85")));

        // required = 0.4 * 100 / (1 - 0.05) = 42.1053
        BomExplosionService.ExplosionResult result = service.explode(10L, new BigDecimal("100"));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).getRequiredQty()).isEqualByComparingTo("42.1053");
        assertThat(result.lines().get(0).getOnHandQty()).isEqualByComparingTo("85");
        assertThat(result.lines().get(0).isSufficient()).isTrue();
        assertThat(result.expiredQtys().get(0)).isEqualByComparingTo("115");
        assertThat(result.estimatedMaterialCost()).isEqualByComparingTo("421.05");
    }

    @Test
    void explode_excludesExpiredLotsFromUsableStock() {
        RawMaterial raw = raw(5L, "NL-BOT", new BigDecimal("100"));
        ProductBomLine bom = bomLine(raw, null, new BigDecimal("1"), null);
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(bom));
        // qtyOnHand=100 nhưng usable chỉ 30 → thiếu khi cần 50
        when(rawMaterialLotRepository.sumUsableRemaining(5L, TODAY)).thenReturn(new BigDecimal("30"));
        when(rawMaterialLotRepository.sumExpiredRemaining(5L, TODAY)).thenReturn(new BigDecimal("70"));
        when(rawMaterialLotRepository.findFefoUsable(eq(5L), eq(TODAY)))
                .thenReturn(List.of(lot(raw, "8", "30")));

        BomExplosionService.ExplosionResult result = service.explode(10L, new BigDecimal("50"));

        assertThat(result.lines().get(0).isSufficient()).isFalse();
        assertThat(result.lines().get(0).getOnHandQty()).isEqualByComparingTo("30");
        assertThat(result.estimatedMaterialCost()).isEqualByComparingTo("240.00");
    }

    @Test
    void explode_waste50Percent_needsDoubleRawMaterial() {
        RawMaterial fish = raw(5L, "NL-CA-THU", new BigDecimal("100"));
        ProductBomLine bom = bomLine(fish, null, BigDecimal.ONE, new BigDecimal("50"));
        stubSingleLine(bom, fish, "100");

        // Chả cá: 1 kg chả, hao hụt 50% → 10 khay quy đổi 1 kg cần 20 kg cá
        BomExplosionService.ExplosionResult result = service.explode(10L, BigDecimal.TEN);

        assertThat(result.lines().get(0).getRequiredQty()).isEqualByComparingTo("20");
        assertThat(result.lines().get(0).getBomQtyPerUnit()).isEqualByComparingTo("1");
    }

    @Test
    void explode_planSkipsPackaging_packagingUsesPhysicalTrays() {
        RawMaterial fish = raw(5L, "NL-CA-THU", new BigDecimal("100"));
        RawMaterial tray = raw(6L, "NL-KHAY", new BigDecimal("100"));
        tray.setUnit("cai");
        ProductBomLine fishLine = bomLine(fish, null, BigDecimal.ONE, new BigDecimal("40"));
        ProductBomLine trayLine = bomLine(tray, null, BigDecimal.ONE, null);
        trayLine.setUnit(unit("cai"));
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(fishLine, trayLine));
        when(rawMaterialLotRepository.sumUsableRemaining(eq(5L), eq(TODAY))).thenReturn(new BigDecimal("100"));
        when(rawMaterialLotRepository.sumUsableRemaining(eq(6L), eq(TODAY))).thenReturn(new BigDecimal("100"));
        when(rawMaterialLotRepository.findFefoUsable(eq(5L), eq(TODAY))).thenReturn(List.of());
        when(rawMaterialLotRepository.findFefoUsable(eq(6L), eq(TODAY))).thenReturn(List.of());

        // Kế hoạch 2 kg quy đổi: chỉ cá (hao 40% → 2/0.6); khay xốp chưa trừ
        BomExplosionService.ExplosionResult plan = service.explode(10L, new BigDecimal("2"));
        assertThat(plan.lines()).hasSize(1);
        assertThat(plan.lines().get(0).getRequiredQty()).isEqualByComparingTo("3.3333");

        // Đóng gói 1×1kg + 2×0,5kg = 3 khay vật lý → 3 khay xốp
        BomExplosionService.ExplosionResult pack = service.explodePackaging(10L, new BigDecimal("3"));
        assertThat(pack.lines()).hasSize(1);
        assertThat(pack.lines().get(0).getRawMaterial().getId()).isEqualTo(6L);
        assertThat(pack.lines().get(0).getRequiredQty()).isEqualByComparingTo("3");
    }

    @Test
    void explode_noVariant_treatsTrayAsOneKg() {
        RawMaterial fish = raw(5L, "NL-CA-THU", new BigDecimal("100"));
        stubSingleLine(bomLine(fish, null, new BigDecimal("2"), null), fish, "100");

        BomExplosionService.ExplosionResult result = service.explode(10L, new BigDecimal("3"));

        assertThat(result.lines().get(0).getRequiredQty()).isEqualByComparingTo("6");
    }

    @Test
    void explode_waste100Percent_returns400() {
        RawMaterial fish = raw(5L, "NL-CA-THU", new BigDecimal("100"));
        ProductBomLine bom = bomLine(fish, null, BigDecimal.ONE, new BigDecimal("100"));
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(bom));

        assertThatThrownBy(() -> service.explode(10L, BigDecimal.ONE))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("nhỏ hơn 100%");
    }

    @Test
    void explode_rejectsComponentProduct() {
        Product component = new Product();
        ReflectionTestUtils.setField(component, "id", 99L);
        ProductBomLine bom = bomLine(null, component, BigDecimal.ONE, null);
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(bom));

        assertThatThrownBy(() -> service.explode(10L, BigDecimal.ONE))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("sản phẩm cấu thành");
    }

    @Test
    void explode_emptyBom_returns400() {
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.explode(10L, BigDecimal.ONE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("BOM");
    }

    private void stubSingleLine(ProductBomLine bom, RawMaterial raw, String usable) {
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(bom));
        when(rawMaterialLotRepository.sumUsableRemaining(eq(raw.getId()), eq(TODAY))).thenReturn(new BigDecimal(usable));
        when(rawMaterialLotRepository.findFefoUsable(eq(raw.getId()), eq(TODAY))).thenReturn(List.of());
    }

    private static UnitOfMeasure unit(String code) {
        UnitOfMeasure u = new UnitOfMeasure();
        u.setCode(code);
        u.setName(code);
        return u;
    }

    private static ProductBomLine bomLine(
            RawMaterial raw, Product component, BigDecimal qty, BigDecimal waste) {
        ProductBomLine line = new ProductBomLine();
        line.setRawMaterial(raw);
        line.setComponentProduct(component);
        line.setQty(qty);
        line.setWastePercent(waste);
        return line;
    }

    private static RawMaterial raw(Long id, String code, BigDecimal onHand) {
        RawMaterial raw = new RawMaterial();
        ReflectionTestUtils.setField(raw, "id", id);
        raw.setCode(code);
        raw.setName(code);
        raw.setUnit("kg");
        raw.setQtyOnHand(onHand);
        return raw;
    }

    private static RawMaterialLot lot(RawMaterial raw, String price, String qty) {
        RawMaterialLot lot = new RawMaterialLot();
        lot.setRawMaterial(raw);
        lot.setLotCode("L1");
        lot.setUnitPrice(new BigDecimal(price));
        lot.setQtyRemaining(new BigDecimal(qty));
        return lot;
    }
}
