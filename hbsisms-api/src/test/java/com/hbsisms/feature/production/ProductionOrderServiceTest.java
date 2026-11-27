package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.GoodsReceiptService;
import com.hbsisms.feature.inventory.RawMaterialStockService;
import com.hbsisms.feature.production.dto.ProductionOrderCreateRequest;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.production.dto.ProductionOrderUpdateRequest;
import com.hbsisms.feature.production.dto.TrayPriceResponse;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductionOrderServiceTest {

    @Mock
    private ProductionOrderRepository productionOrderRepository;
    @Mock
    private ProductionOrderMaterialLineRepository materialLineRepository;
    @Mock
    private ProductionLeftoverRepository leftoverRepository;
    @Mock
    private MaterialIssueLineRepository materialIssueLineRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private BomExplosionService bomExplosionService;
    @Mock
    private MaterialIssueService materialIssueService;
    @Mock
    private GoodsReceiptService goodsReceiptService;
    @Mock
    private RawMaterialStockService rawMaterialStockService;
    @Mock
    private com.hbsisms.feature.costing.CostingService costingService;
    @Mock
    private BusinessCodeService businessCodeService;
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserRepository userRepository;

    private ProductionOrderService service;

    @BeforeEach
    void setUp() {
        service = new ProductionOrderService(
                productionOrderRepository,
                materialLineRepository,
                leftoverRepository,
                materialIssueLineRepository,
                productRepository,
                productVariantRepository,
                bomExplosionService,
                materialIssueService,
                goodsReceiptService,
                rawMaterialStockService,
                costingService,
                businessCodeService,
                appConfigService,
                auditLogService,
                userRepository,
                Clock.fixed(Instant.parse("2026-11-23T03:00:00Z"), ZoneOffset.UTC));
        lenient().when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(anyLong())).thenReturn(List.of());
        lenient()
                .when(costingService.resolveRawIssueUnitCost(any(), any()))
                .thenAnswer(inv -> {
                    java.math.BigDecimal lot = inv.getArgument(1);
                    return lot == null ? java.math.BigDecimal.ZERO : lot;
                });
        lenient().when(appConfigService.sellPriceMarkupPercent()).thenReturn(30);
    }

    @Test
    void create_withSufficientStock_returnsDraftAndDu() {
        Product product = product(10L, "TP-001", "Bánh quy");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2026-0001");
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(true, "42"));
        when(productionOrderRepository.save(any())).thenAnswer(inv -> {
            ProductionOrder o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 100L);
            return o;
        });

        ProductionOrderResponse response = service.create(
                new ProductionOrderCreateRequest(
                        10L, new BigDecimal("100"), LocalDate.of(2026, 11, 28), "mẻ thử", new BigDecimal("500000"), null),
                1L);

        assertThat(response.code()).isEqualTo("LSX-2026-0001");
        assertThat(response.status()).isEqualTo(ProductionOrderStatus.DRAFT);
        assertThat(response.allMaterialsSufficient()).isTrue();
        assertThat(response.materials()).hasSize(1);
        assertThat(response.materials().get(0).stockStatus()).isEqualTo("Đủ");
        assertThat(response.estimatedMaterialCost()).isEqualByComparingTo("1000");
    }

    @Test
    void create_withShortage_stillSavesDraftAsThieu() {
        Product product = product(10L, "TP-001", "Bánh quy");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2026-0002");
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(false, "42"));
        when(productionOrderRepository.save(any())).thenAnswer(inv -> {
            ProductionOrder o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 101L);
            return o;
        });

        ProductionOrderResponse response = service.create(
                new ProductionOrderCreateRequest(10L, new BigDecimal("100"), null, null, null, null), null);

        assertThat(response.status()).isEqualTo(ProductionOrderStatus.DRAFT);
        assertThat(response.allMaterialsSufficient()).isFalse();
        assertThat(response.materials().get(0).stockStatus()).isEqualTo("Thiếu");
    }

    @Test
    void create_whenBomHasComponent_propagates400() {
        Product product = product(10L, "TP-001", "Bánh quy");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(bomExplosionService.explode(eq(10L), any()))
                .thenThrow(ApiException.badRequest("BOM chứa sản phẩm cấu thành — hiện chỉ hỗ trợ nguyên liệu thô"));

        assertThatThrownBy(() -> service.create(
                        new ProductionOrderCreateRequest(10L, BigDecimal.ONE, null, null, null, null), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(productionOrderRepository, never()).save(any());
    }

    @Test
    void create_whenBomEmpty_propagates400() {
        Product product = product(10L, "TP-001", "Bánh quy");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(bomExplosionService.explode(eq(10L), any()))
                .thenThrow(ApiException.badRequest("Sản phẩm chưa có định mức nguyên liệu (BOM)"));

        assertThatThrownBy(() -> service.create(
                        new ProductionOrderCreateRequest(10L, BigDecimal.ONE, null, null, null, null), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("BOM");
    }

    @Test
    void approve_whenAllSufficient_createsPendingMaterialIssue() {
        ProductionOrder order = draftOrder(true);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(true, "42"));
        MaterialIssue issue = new MaterialIssue();
        ReflectionTestUtils.setField(issue, "id", 50L);
        issue.setCode("PX-2026-0001");
        issue.setStatus(MaterialIssueStatus.PENDING);
        when(materialIssueService.createPendingFromOrder(any(), any())).thenAnswer(inv -> {
            ProductionOrder o = inv.getArgument(0);
            o.setMaterialIssue(issue);
            issue.setProductionOrder(o);
            return issue;
        });

        ProductionOrderResponse response = service.approve(100L, 1L);

        assertThat(response.status()).isEqualTo(ProductionOrderStatus.APPROVED);
        assertThat(response.materialIssueCode()).isEqualTo("PX-2026-0001");
        verify(materialIssueService).createPendingFromOrder(order, 1L);
    }

    @Test
    void approve_whenShortage_returns409AndDoesNotCreateIssue() {
        ProductionOrder order = draftOrder(false);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(false, "42"));

        assertThatThrownBy(() -> service.approve(100L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("Không đủ nguyên liệu");
        verify(materialIssueService, never()).createPendingFromOrder(any(), anyLong());
    }

    @Test
    void approve_whenNotDraft_returns409() {
        ProductionOrder order = draftOrder(true);
        order.setStatus(ProductionOrderStatus.APPROVED);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.approve(100L, 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(bomExplosionService, never()).explode(anyLong(), any());
    }

    @Test
    void update_draft_recomputesBom() {
        ProductionOrder order = draftOrder(true);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(true, "84"));

        ProductionOrderResponse response = service.update(
                100L,
                new ProductionOrderUpdateRequest(new BigDecimal("200"), null, "sửa", null, null));

        assertThat(response.plannedQty()).isEqualByComparingTo("200");
        verify(bomExplosionService).explode(10L, new BigDecimal("200"));
        assertThat(response.materials().get(0).requiredQty()).isEqualByComparingTo("84");
    }

    @Test
    void create_fractionalTrays_returns400_withoutSaving() {
        Product product = product(10L, "TP-001", "Bánh quy");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.create(
                        new ProductionOrderCreateRequest(10L, new BigDecimal("1.5"), null, null, null, null), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("số khay nguyên")
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(bomExplosionService, never()).explode(anyLong(), any());
        verify(productionOrderRepository, never()).save(any());
    }

    @Test
    void update_fractionalTrays_returns400_wholeWithTrailingZerosOk() {
        ProductionOrder order = draftOrder(true);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(bomExplosionService.explode(eq(10L), any())).thenReturn(explosion(true, "84"));

        assertThatThrownBy(() -> service.update(
                        100L, new ProductionOrderUpdateRequest(new BigDecimal("2.5"), null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("số khay nguyên");
        // 2.0000 (cách DB lưu numeric) vẫn là số nguyên.
        assertThat(service.update(100L, new ProductionOrderUpdateRequest(new BigDecimal("2.0000"), null, null, null, null))
                        .plannedQty())
                .isEqualByComparingTo("2");
    }

    @Test
    void update_approved_returns409() {
        ProductionOrder order = draftOrder(true);
        order.setStatus(ProductionOrderStatus.APPROVED);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.update(
                        100L, new ProductionOrderUpdateRequest(BigDecimal.ONE, null, null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void get_completed_suggestsSellPriceFromUnitCostAndMarkup() {
        ProductionOrder order = draftOrder(true);
        order.setStatus(ProductionOrderStatus.COMPLETED);
        order.setActualQty(new BigDecimal("10"));
        order.setActualMaterialCost(new BigDecimal("70000"));
        order.setActualProductionCost(new BigDecimal("30000"));
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(appConfigService.sellPriceMarkupPercent()).thenReturn(30);
        when(appConfigService.sellPriceFromUnitCost(any())).thenReturn(new BigDecimal("13000"));

        ProductionOrderResponse response = service.get(100L);

        assertThat(response.unitCost()).isEqualByComparingTo("10000");
        assertThat(response.markupPercent()).isEqualTo(30);
        assertThat(response.suggestedSellPrice()).isEqualByComparingTo("13000");
    }

    @Test
    void create_ignoresVariantId_leavesFinishedVariantNull() {
        Product product = product(10L, "TP-001", "Chả cá");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2026-0003");
        when(bomExplosionService.explode(10L, new BigDecimal("6"))).thenReturn(explosion(true, "5"));
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductionOrderResponse response = service.create(
                new ProductionOrderCreateRequest(10L, new BigDecimal("6"), null, null, null, 21L), null);

        assertThat(response.finishedVariantId()).isNull();
        verify(bomExplosionService).explode(10L, new BigDecimal("6"));
        verify(productVariantRepository, never()).findById(any());
    }

    @Test
    void create_withoutTray_keepsFinishedVariantNull() {
        Product product = product(10L, "TP-001", "Chả cá");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(businessCodeService.nextProductionOrderCode()).thenReturn("LSX-2026-0004");
        when(bomExplosionService.explode(10L, BigDecimal.ONE)).thenReturn(explosion(true, "1"));
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProductionOrderResponse response = service.create(
                new ProductionOrderCreateRequest(10L, BigDecimal.ONE, null, null, null, null), null);

        assertThat(response.finishedVariantId()).isNull();
    }

    @Test
    void approve_reExplodesWeightOnly() {
        ProductionOrder order = draftOrder(true);
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        when(bomExplosionService.explode(10L, new BigDecimal("100"))).thenReturn(explosion(true, "42"));

        service.approve(100L, null);

        verify(bomExplosionService).explode(10L, new BigDecimal("100"));
    }

    @Test
    void get_completedHalfKgTray_suggestsOneKgPriceFromCostPerKg() {
        ProductionOrder order = draftOrder(true);
        ProductVariant half = variant(order.getProduct(), 21L, 500);
        ProductVariant oneKg = variant(order.getProduct(), 22L, 1000);
        ProductVariant stopped = variant(order.getProduct(), 23L, 2000);
        stopped.setActive(false);
        half.setListPrice(new BigDecimal("15000"));
        oneKg.setListPrice(new BigDecimal("30000"));
        order.setFinishedVariant(half);
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(half, oneKg, stopped));
        order.setStatus(ProductionOrderStatus.COMPLETED);
        order.setActualQty(new BigDecimal("10"));
        order.setActualMaterialCost(new BigDecimal("70000"));
        order.setActualProductionCost(new BigDecimal("30000"));
        when(productionOrderRepository.findWithHeaderById(100L)).thenReturn(Optional.of(order));
        // giá vốn 10.000/khay 0,5 kg → 20.000/kg → giá 1 kg 26.000 (màn hoàn thành tự chia đôi ra khay 0,5 kg)
        when(appConfigService.sellPriceFromUnitCost(argThat(c -> c.compareTo(new BigDecimal("20000")) == 0)))
                .thenReturn(new BigDecimal("26000"));

        ProductionOrderResponse response = service.get(100L);

        assertThat(response.unitCost()).isEqualByComparingTo("10000");
        assertThat(response.finishedTrayKg()).isEqualByComparingTo("0.5");
        assertThat(response.suggestedSellPrice()).isEqualByComparingTo("26000");
        // Khay 1 kg trước; khay ngừng bán không hiện; giá đang bán giữ nguyên, chỉ kèm giá gợi ý
        assertThat(response.trayPrices()).extracting(TrayPriceResponse::variantId).containsExactly(22L, 21L);
        assertThat(response.trayPrices().get(0).listPrice()).isEqualByComparingTo("30000");
        assertThat(response.trayPrices().get(0).suggestedPrice()).isEqualByComparingTo("26000");
        assertThat(response.trayPrices().get(0).produced()).isFalse();
        assertThat(response.trayPrices().get(1).listPrice()).isEqualByComparingTo("15000");
        assertThat(response.trayPrices().get(1).suggestedPrice()).isEqualByComparingTo("13000");
        assertThat(response.trayPrices().get(1).produced()).isTrue();
    }

    private static ProductVariant variant(Product product, Long id, int grams) {
        ProductVariant v = new ProductVariant();
        ReflectionTestUtils.setField(v, "id", id);
        v.setProduct(product);
        v.setSku("SKU-" + id);
        v.setUnitWeightG(grams);
        v.setActive(true);
        return v;
    }

    private ProductionOrder draftOrder(boolean sufficient) {
        ProductionOrder order = new ProductionOrder();
        ReflectionTestUtils.setField(order, "id", 100L);
        order.setCode("LSX-2026-0001");
        order.setProduct(product(10L, "TP-001", "Bánh quy"));
        order.setPlannedQty(new BigDecimal("100"));
        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setEstimatedMaterialCost(new BigDecimal("1000"));
        order.replaceMaterialLines(explosion(sufficient, "42").lines());
        return order;
    }

    private BomExplosionService.ExplosionResult explosion(boolean sufficient, String required) {
        var raw = new com.hbsisms.feature.inventory.RawMaterial();
        ReflectionTestUtils.setField(raw, "id", 5L);
        raw.setCode("NL-BOT");
        raw.setName("Bột mì");
        raw.setUnit("kg");
        raw.setQtyOnHand(sufficient ? new BigDecimal("100") : new BigDecimal("1"));

        ProductionOrderMaterialLine line = new ProductionOrderMaterialLine();
        line.setRawMaterial(raw);
        line.setBomQtyPerUnit(new BigDecimal("0.42"));
        line.setWastePercent(BigDecimal.ZERO);
        line.setRequiredQty(new BigDecimal(required));
        line.setOnHandQty(raw.getQtyOnHand());
        line.setSufficient(sufficient);
        line.setLineEstimatedCost(new BigDecimal("1000"));
        return new BomExplosionService.ExplosionResult(new ArrayList<>(List.of(line)), new BigDecimal("1000"));
    }

    private static Product product(Long id, String code, String name) {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", id);
        p.setCode(code);
        p.setName(name);
        p.setActive(true);
        return p;
    }
}
