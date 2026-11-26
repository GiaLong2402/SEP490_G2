package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptService;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialStockService;
import com.hbsisms.feature.inventory.ReceiptStatus;
import com.hbsisms.feature.inventory.ReceiptType;
import com.hbsisms.feature.production.dto.ProductionLeftoverInput;
import com.hbsisms.feature.production.dto.ProductionOrderCompleteRequest;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Cường — Inter 1 task 51: báo hoàn thành + leftover -TL + PN TP Pending. */
@ExtendWith(MockitoExtension.class)
class ProductionOrderCompleteServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

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
                businessCodeService,
                appConfigService,
                auditLogService,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void complete_createsPendingFinishedReceipt_andAwaitingReceipt() {
        ProductionOrder order = inProgressOrder(7L);
        ProductVariant variant = variant(order.getProduct(), 33L);
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        GoodsReceipt receipt = new GoodsReceipt();
        ReflectionTestUtils.setField(receipt, "id", 88L);
        receipt.setCode("PN-2026-0088");
        receipt.setReceiptType(ReceiptType.FINISHED);
        receipt.setStatus(ReceiptStatus.PENDING);
        when(goodsReceiptService.createPendingFinishedFromProduction(
                        eq(7L), eq("LSX-7"), eq(variant), eq(new BigDecimal("12")), eq(1L)))
                .thenReturn(receipt);
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productionOrderRepository.findWithHeaderById(7L)).thenReturn(Optional.of(order));
        when(materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());

        ProductionOrderResponse response = service.complete(
                7L,
                new ProductionOrderCompleteRequest(
                        new BigDecimal("12"),
                        new BigDecimal("90000"),
                        null,
                        LocalDate.of(2026, 11, 22),
                        null,
                        null,
                        List.of()),
                1L);

        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.AWAITING_RECEIPT);
        assertThat(order.getActualQty()).isEqualByComparingTo("12");
        assertThat(order.getActualProductionCost()).isEqualByComparingTo("90000");
        assertThat(order.getFinishedDate()).isEqualTo(LocalDate.of(2026, 11, 22));
        assertThat(order.getFinishedReceipt()).isSameAs(receipt);
        assertThat(response.finishedReceiptCode()).isEqualTo("PN-2026-0088");
        assertThat(response.status()).isEqualTo(ProductionOrderStatus.AWAITING_RECEIPT);
    }

    @Test
    void complete_withLeftover_createsTlLot_andCreditsMaterialCost() {
        ProductionOrder order = inProgressOrder(7L);
        order.setActualMaterialCost(new BigDecimal("10000.00"));
        ProductVariant variant = variant(order.getProduct(), 33L);
        RawMaterial raw = raw(9L);
        raw.setStorageLocation(zone("LANH")); // đồ khu lạnh → tách lô -TL
        RawMaterialLot source = lot(raw, 20L, "L-A", new BigDecimal("2000"), LocalDate.of(2026, 12, 1));
        MaterialIssueLine issued = issuedLine(raw, source, "5");

        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L))
                .thenReturn(List.of(issued));
        RawMaterialLot returned = lot(raw, 99L, "L-A-TL", new BigDecimal("2000"), TODAY.plusDays(1));
        when(rawMaterialStockService.nextLeftoverLotCode(raw, "L-A")).thenReturn("L-A-TL");
        when(rawMaterialStockService.receive(
                        eq(raw),
                        eq("L-A-TL"),
                        isNull(),
                        eq(TODAY.plusDays(1)),
                        eq(new BigDecimal("2000")),
                        eq(new BigDecimal("2")),
                        isNull(),
                        isNull(),
                        any()))
                .thenReturn(returned);
        when(leftoverRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        GoodsReceipt receipt = new GoodsReceipt();
        ReflectionTestUtils.setField(receipt, "id", 88L);
        receipt.setCode("PN-88");
        when(goodsReceiptService.createPendingFinishedFromProduction(any(), any(), any(), any(), any()))
                .thenReturn(receipt);
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productionOrderRepository.findWithHeaderById(7L)).thenReturn(Optional.of(order));
        when(materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());

        service.complete(
                7L,
                new ProductionOrderCompleteRequest(
                        new BigDecimal("10"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        List.of(new ProductionLeftoverInput(9L, new BigDecimal("2"), TODAY.plusDays(1)))),
                null);

        assertThat(order.getActualMaterialCost()).isEqualByComparingTo("6000.00");
        assertThat(order.getFinishedDate()).isEqualTo(TODAY);
        verify(rawMaterialStockService).receive(
                eq(raw),
                eq("L-A-TL"),
                isNull(),
                eq(TODAY.plusDays(1)),
                eq(new BigDecimal("2000")),
                eq(new BigDecimal("2")),
                isNull(),
                isNull(),
                any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductionLeftover>> leftoverCap = ArgumentCaptor.forClass(List.class);
        verify(leftoverRepository).saveAll(leftoverCap.capture());
        assertThat(leftoverCap.getValue()).hasSize(1);
        assertThat(leftoverCap.getValue().get(0).getAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void complete_dryLeftover_returnsToIssuedLotsNewestFirst_withoutNewLot() {
        ProductionOrder order = inProgressOrder(7L);
        order.setActualMaterialCost(new BigDecimal("10000.00"));
        ProductVariant variant = variant(order.getProduct(), 33L);
        RawMaterial film = raw(9L);
        film.setStorageLocation(zone("KHO")); // khu khô → trả về lô gốc
        RawMaterialLot older = lot(film, 20L, "L-A", new BigDecimal("1000"), LocalDate.of(2027, 1, 1));
        RawMaterialLot newer = lot(film, 21L, "L-B", new BigDecimal("1500"), LocalDate.of(2027, 6, 1));
        MaterialIssueLine first = issuedLine(film, older, "0.5");
        MaterialIssueLine second = issuedLine(film, newer, "0.55");
        ReflectionTestUtils.setField(second, "id", 2L);

        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L))
                .thenReturn(List.of(first, second));
        when(rawMaterialStockService.returnToLot(eq(21L), any(), any(), eq(7L), any(), any())).thenReturn(newer);
        when(rawMaterialStockService.returnToLot(eq(20L), any(), any(), eq(7L), any(), any())).thenReturn(older);
        when(leftoverRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        GoodsReceipt receipt = new GoodsReceipt();
        ReflectionTestUtils.setField(receipt, "id", 88L);
        receipt.setCode("PN-88");
        when(goodsReceiptService.createPendingFinishedFromProduction(any(), any(), any(), any(), any()))
                .thenReturn(receipt);
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productionOrderRepository.findWithHeaderById(7L)).thenReturn(Optional.of(order));
        when(materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());

        // Dư 0,8: trả lô xuất sau cùng (L-B) tối đa 0,55, phần còn 0,25 về L-A. Không cần hạn.
        service.complete(
                7L,
                new ProductionOrderCompleteRequest(
                        new BigDecimal("10"), BigDecimal.ZERO, null, null, null, null,
                        List.of(new ProductionLeftoverInput(9L, new BigDecimal("0.8"), null))),
                null);

        verify(rawMaterialStockService).returnToLot(
                eq(21L), eq(new BigDecimal("0.55")), eq(RawMaterialStockService.REF_PRODUCTION_ORDER), eq(7L), any(), any());
        verify(rawMaterialStockService).returnToLot(
                eq(20L), eq(new BigDecimal("0.25")), eq(RawMaterialStockService.REF_PRODUCTION_ORDER), eq(7L), any(), any());
        verify(rawMaterialStockService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(rawMaterialStockService, never()).nextLeftoverLotCode(any(), any());
        // 0,55 × 1.500 + 0,25 × 1.000 = 825 + 250 = 1.075 → 10.000 − 1.075
        assertThat(order.getActualMaterialCost()).isEqualByComparingTo("8925.00");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductionLeftover>> leftoverCap = ArgumentCaptor.forClass(List.class);
        verify(leftoverRepository).saveAll(leftoverCap.capture());
        assertThat(leftoverCap.getValue()).extracting(l -> l.getLot().getLotCode()).containsExactly("L-B", "L-A");
        assertThat(leftoverCap.getValue()).extracting(ProductionLeftover::getExpDate)
                .containsExactly(LocalDate.of(2027, 6, 1), LocalDate.of(2027, 1, 1));
    }

    @Test
    void complete_coldLeftoverWithoutExpDate_returns400() {
        ProductionOrder order = inProgressOrder(7L);
        RawMaterial squid = raw(9L);
        squid.setStorageLocation(zone("LANH-2"));
        RawMaterialLot source = lot(squid, 20L, "L-A", new BigDecimal("2000"), LocalDate.of(2026, 12, 1));
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L))
                .thenReturn(List.of(variant(order.getProduct(), 33L)));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L))
                .thenReturn(List.of(issuedLine(squid, source, "5")));

        assertThatThrownBy(() -> service.complete(
                        7L,
                        new ProductionOrderCompleteRequest(
                                new BigDecimal("10"), BigDecimal.ZERO, null, null, null, null,
                                List.of(new ProductionLeftoverInput(9L, new BigDecimal("1"), null))),
                        null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("hạn dùng lô dư");
        verify(rawMaterialStockService, never()).returnToLot(any(), any(), any(), any(), any(), any());
    }

    @Test
    void complete_withDefect_persistsQtyAndReason() {
        ProductionOrder order = inProgressOrder(7L);
        ProductVariant variant = variant(order.getProduct(), 33L);
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        GoodsReceipt receipt = new GoodsReceipt();
        ReflectionTestUtils.setField(receipt, "id", 1L);
        receipt.setCode("PN-1");
        when(goodsReceiptService.createPendingFinishedFromProduction(any(), any(), any(), any(), any()))
                .thenReturn(receipt);
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productionOrderRepository.findWithHeaderById(7L)).thenReturn(Optional.of(order));
        when(materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());

        service.complete(
                7L,
                new ProductionOrderCompleteRequest(
                        new BigDecimal("8"),
                        null,
                        null,
                        TODAY,
                        new BigDecimal("2"),
                        "Vỡ khay",
                        List.of()),
                null);

        assertThat(order.getDefectQty()).isEqualByComparingTo("2");
        assertThat(order.getDefectReason()).isEqualTo("Vỡ khay");
    }

    @Test
    void complete_leftoverOverIssued_returns400() {
        ProductionOrder order = inProgressOrder(7L);
        ProductVariant variant = variant(order.getProduct(), 33L);
        RawMaterial raw = raw(9L);
        RawMaterialLot source = lot(raw, 20L, "L-A", new BigDecimal("1000"), LocalDate.of(2026, 12, 1));
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L))
                .thenReturn(List.of(issuedLine(raw, source, "3")));

        assertThatThrownBy(() -> service.complete(
                        7L,
                        new ProductionOrderCompleteRequest(
                                BigDecimal.ONE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                List.of(new ProductionLeftoverInput(9L, new BigDecimal("5"), TODAY.plusDays(1)))),
                        null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(goodsReceiptService, never()).createPendingFinishedFromProduction(any(), any(), any(), any(), any());
    }

    @Test
    void complete_whenNotInProgress_returns409() {
        ProductionOrder order = inProgressOrder(7L);
        order.setStatus(ProductionOrderStatus.APPROVED);
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.complete(
                        7L,
                        new ProductionOrderCompleteRequest(BigDecimal.ONE, null, null, null, null, null, null),
                        null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(goodsReceiptService, never()).createPendingFinishedFromProduction(any(), any(), any(), any(), any());
    }

    @Test
    void complete_whenAlreadyHasReceipt_isIdempotent() {
        ProductionOrder order = inProgressOrder(7L);
        GoodsReceipt receipt = new GoodsReceipt();
        ReflectionTestUtils.setField(receipt, "id", 88L);
        receipt.setCode("PN-OLD");
        order.setFinishedReceipt(receipt);
        order.setStatus(ProductionOrderStatus.AWAITING_RECEIPT);
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));
        when(productionOrderRepository.findWithHeaderById(7L)).thenReturn(Optional.of(order));
        when(materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(7L)).thenReturn(List.of());
        when(leftoverRepository.findByProductionOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());

        ProductionOrderResponse response = service.complete(
                7L,
                new ProductionOrderCompleteRequest(new BigDecimal("99"), null, null, null, null, null, null),
                null);

        assertThat(response.finishedReceiptCode()).isEqualTo("PN-OLD");
        verify(goodsReceiptService, never()).createPendingFinishedFromProduction(any(), any(), any(), any(), any());
    }

    private static ProductionOrder inProgressOrder(Long id) {
        ProductionOrder order = new ProductionOrder();
        ReflectionTestUtils.setField(order, "id", id);
        order.setCode("LSX-" + id);
        order.setStatus(ProductionOrderStatus.IN_PROGRESS);
        order.setPlannedQty(BigDecimal.TEN);
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setCode("TP");
        product.setName("TP");
        order.setProduct(product);
        MaterialIssue issue = new MaterialIssue();
        ReflectionTestUtils.setField(issue, "id", 5L);
        issue.setCode("PX-5");
        issue.setStatus(MaterialIssueStatus.COMPLETED);
        issue.setProductionOrder(order);
        order.setMaterialIssue(issue);
        return order;
    }

    private static ProductVariant variant(Product product, Long id) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setProduct(product);
        variant.setSku("SKU-" + id);
        variant.setPackLabel("Khay");
        variant.setListPrice(BigDecimal.TEN);
        variant.setActive(true);
        return variant;
    }

    private static RawMaterial raw(Long id) {
        RawMaterial raw = new RawMaterial();
        ReflectionTestUtils.setField(raw, "id", id);
        raw.setCode("NL-" + id);
        raw.setName("NVL");
        raw.setUnit("kg");
        return raw;
    }

    private static RawMaterialLot lot(
            RawMaterial raw, Long id, String code, BigDecimal price, LocalDate exp) {
        RawMaterialLot lot = new RawMaterialLot();
        ReflectionTestUtils.setField(lot, "id", id);
        lot.setRawMaterial(raw);
        lot.setLotCode(code);
        lot.setUnitPrice(price);
        lot.setExpDate(exp);
        lot.setQtyRemaining(BigDecimal.TEN);
        return lot;
    }

    private static Location zone(String code) {
        Location location = new Location();
        location.setCode(code);
        return location;
    }

    private static MaterialIssueLine issuedLine(RawMaterial raw, RawMaterialLot lot, String qty) {
        MaterialIssueLine line = new MaterialIssueLine();
        ReflectionTestUtils.setField(line, "id", 1L);
        line.setRawMaterial(raw);
        line.setRawMaterialLot(lot);
        line.setQtyRequired(new BigDecimal(qty));
        line.setQtyIssued(new BigDecimal(qty));
        return line;
    }
}
