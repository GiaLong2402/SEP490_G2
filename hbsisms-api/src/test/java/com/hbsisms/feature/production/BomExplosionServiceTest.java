package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
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

    private BomExplosionService service;

    @BeforeEach
    void setUp() {
        service = new BomExplosionService(bomLineRepository, rawMaterialLotRepository, CLOCK);
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

        // required = 0.4 * 100 * 1.05 = 42
        BomExplosionService.ExplosionResult result = service.explode(10L, new BigDecimal("100"));

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).getRequiredQty()).isEqualByComparingTo("42.0000");
        assertThat(result.lines().get(0).getOnHandQty()).isEqualByComparingTo("85");
        assertThat(result.lines().get(0).isSufficient()).isTrue();
        assertThat(result.expiredQtys().get(0)).isEqualByComparingTo("115");
        assertThat(result.estimatedMaterialCost()).isEqualByComparingTo("420.00");
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
