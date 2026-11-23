package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.FinishedReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteLineRequest;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.RawReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.ReceiptItemResponse;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;
    @Mock
    private GoodsReceiptLineRepository goodsReceiptLineRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private LotRepository lotRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private RawMaterialStockService rawMaterialStockService;
    @Mock
    private StockService stockService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private AuditLogService auditLogService;

    private GoodsReceiptService service;
    private ProductVariant variant;
    private Location location;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptService(
                goodsReceiptRepository,
                goodsReceiptLineRepository,
                stockLedgerRepository,
                productVariantRepository,
                locationRepository,
                lotRepository,
                rawMaterialRepository,
                rawMaterialStockService,
                stockService,
                userRepository,
                orderAllocationService,
                auditLogService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        setId(variant, 1L);
        variant.setSku("TOM-1");
        variant.setProduct(product);
        location = new Location();
        setId(location, 10L);
        location.setCode("A-01");
        actor = new AppUser();
        setId(actor, 1L);
        actor.setFullName("Thủ kho");
    }

    // ---- Thành phẩm: giữ nguyên hành vi nhập ngay ----

    @Test
    void createFinished_callsReceiveInPerLineWithReceiptRef_andIsCompleted() {
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.empty());
        stubSaveAndFlush(5L);
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(lotRepository.findByLotCodeIgnoreCase("LOT-A")).thenReturn(Optional.empty());
        when(lotRepository.saveAndFlush(any(Lot.class))).thenAnswer(inv -> {
            Lot lot = inv.getArgument(0);
            setId(lot, 7L);
            return lot;
        });
        when(stockService.receiveIn(any())).thenReturn(new Stock());
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        GoodsReceiptResponse response = service.create(
                new GoodsReceiptRequest(
                        ReceiptType.FINISHED,
                        "Xưởng demo",
                        null,
                        List.of(new FinishedReceiptLineRequest(
                                1L, "LOT-A", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), 10L, 15)),
                        null,
                        null),
                1L);

        assertThat(response.code()).isEqualTo("PN-0001");
        assertThat(response.supplierName()).isEqualTo("Xưởng demo");
        assertThat(response.items()).hasSize(1);
        assertThat(response.status()).isEqualTo(ReceiptStatus.COMPLETED);

        ArgumentCaptor<StockMutation> captor = ArgumentCaptor.forClass(StockMutation.class);
        verify(stockService).receiveIn(captor.capture());
        assertThat(captor.getValue().refType()).isEqualTo("goods_receipt");
        assertThat(captor.getValue().refId()).isEqualTo(5L);
        assertThat(captor.getValue().qty()).isEqualTo(15);
        verify(orderAllocationService).allocateOutstandingAfterReceipt(1L);
    }

    @Test
    void createFinished_rejectsEmptyLines() {
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.empty());
        stubSaveAndFlush(1L);

        assertStatus(
                () -> service.create(
                        new GoodsReceiptRequest(ReceiptType.FINISHED, "Nguồn", null, List.of(), null, null), null),
                HttpStatus.BAD_REQUEST,
                "ít nhất một dòng");
    }

    // ---- Inter 1 dòng 46: tạo phiếu NVL chờ nhập ----

    @Test
    void createRaw_isPending_savesLines_andDoesNotTouchStock() {
        RawMaterial ice = material(3L, "Đá lạnh", "kg");
        RawMaterial salt = material(4L, "Muối", "bao");
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.of("PN-0012"));
        stubSaveAndFlush(9L);
        when(rawMaterialRepository.findById(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialRepository.findById(4L)).thenReturn(Optional.of(salt));
        when(goodsReceiptLineRepository.saveAll(any())).thenAnswer(inv -> {
            List<GoodsReceiptLine> lines = new ArrayList<>(inv.getArgument(0));
            for (int i = 0; i < lines.size(); i++) {
                setId(lines.get(i), 100L + i);
            }
            return lines;
        });

        GoodsReceiptResponse created = service.create(
                new GoodsReceiptRequest(
                        ReceiptType.RAW,
                        "Chợ đầu mối",
                        "Giao sáng",
                        null,
                        List.of(
                                new RawReceiptLineRequest(3L, new BigDecimal("12.5")),
                                new RawReceiptLineRequest(4L, new BigDecimal("2"))),
                        LocalDate.of(2026, 11, 25)),
                null);

        assertThat(created.code()).isEqualTo("PN-0013");
        assertThat(created.status()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(created.expectedDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(created.note()).isEqualTo("Giao sáng");
        assertThat(created.totalAmount()).isNull();
        assertThat(created.lines()).hasSize(2);
        assertThat(created.lines().get(0).rawMaterialName()).isEqualTo("Đá lạnh");
        assertThat(created.lines().get(0).qtyExpected()).isEqualByComparingTo("12.5");
        assertThat(created.lines().get(0).qtyReceived()).isNull();
        assertThat(created.items()).extracting(ReceiptItemResponse::name).containsExactly("Đá lạnh", "Muối");
        // Chưa cộng tồn: không gọi nhập lô, tồn NVL giữ nguyên.
        verify(rawMaterialStockService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(ice.getQtyOnHand()).isEqualByComparingTo("0");
        verify(auditLogService).record(isNull(), eq("create"), eq("goods_receipt"), eq(9L), anyMap());
    }

    @Test
    void createRaw_rejectsDuplicateMaterialZeroQtyAndInactiveMaterial() {
        RawMaterial ice = material(3L, "Đá lạnh", "kg");
        RawMaterial old = material(5L, "Bao cũ", "cái");
        old.setActive(false);
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.empty());
        when(rawMaterialRepository.findById(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialRepository.findById(5L)).thenReturn(Optional.of(old));

        assertStatus(() -> service.create(rawRequest(List.of(
                        new RawReceiptLineRequest(3L, BigDecimal.ONE), new RawReceiptLineRequest(3L, BigDecimal.TEN))), null),
                HttpStatus.BAD_REQUEST, "một dòng");
        assertStatus(() -> service.create(rawRequest(List.of(new RawReceiptLineRequest(3L, BigDecimal.ZERO))), null),
                HttpStatus.BAD_REQUEST, "lớn hơn 0");
        assertStatus(() -> service.create(rawRequest(List.of(new RawReceiptLineRequest(5L, BigDecimal.ONE))), null),
                HttpStatus.CONFLICT, "ngừng dùng");
        assertStatus(() -> service.create(rawRequest(List.of()), null), HttpStatus.BAD_REQUEST, "ít nhất một dòng");
        verify(goodsReceiptRepository, never()).saveAndFlush(any());
    }

    // ---- Inter 1 dòng 47: xác nhận nhập kho NVL ----

    @Test
    void complete_receivesLotPerLine_marksCompleted_andComputesTotal() {
        GoodsReceipt receipt = pendingReceipt(20L);
        RawMaterial ice = material(3L, "Đá lạnh", "kg");
        RawMaterial salt = material(4L, "Muối", "bao");
        GoodsReceiptLine iceLine = line(200L, receipt, ice, 1, "10");
        GoodsReceiptLine saltLine = line(201L, receipt, salt, 2, "5");
        when(goodsReceiptRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(receipt));
        when(goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(20L)).thenReturn(List.of(iceLine, saltLine));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(rawMaterialRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(salt));
        when(rawMaterialStockService.receive(eq(ice), eq("DA-01"), any(), any(), any(), any(), eq(receipt), eq(actor), anyString()))
                .thenReturn(lot(300L, ice, "DA-01"));
        when(rawMaterialStockService.receive(eq(salt), isNull(), any(), any(), any(), any(), eq(receipt), eq(actor), anyString()))
                .thenReturn(lot(301L, salt, "NVL-261123-01"));

        GoodsReceiptResponse done = service.complete(20L, new RawReceiptCompleteRequest(List.of(
                        new RawReceiptCompleteLineRequest(200L, new BigDecimal("9.5"), "DA-01",
                                LocalDate.of(2026, 11, 22), LocalDate.of(2026, 12, 5), new BigDecimal("4000")),
                        new RawReceiptCompleteLineRequest(201L, new BigDecimal("5"), null, null, null, new BigDecimal("15000"))),
                "Thiếu 0.5kg đá"), 1L);

        assertThat(done.status()).isEqualTo(ReceiptStatus.COMPLETED);
        assertThat(done.completerName()).isEqualTo("Thủ kho");
        assertThat(done.completedAt()).isEqualTo(NOW);
        assertThat(done.note()).isEqualTo("Thiếu 0.5kg đá");
        // 9.5 × 4.000 + 5 × 15.000 = 113.000
        assertThat(done.totalAmount()).isEqualByComparingTo("113000");
        assertThat(done.lines()).extracting(l -> l.lotCode()).containsExactly("DA-01", "NVL-261123-01");
        assertThat(done.items().get(0).qty()).isEqualTo(9.5);
        assertThat(iceLine.getQtyReceived()).isEqualByComparingTo("9.5");
        assertThat(iceLine.getRawLot().getId()).isEqualTo(300L);
        verify(rawMaterialStockService).receive(eq(ice), eq("DA-01"), eq(LocalDate.of(2026, 11, 22)),
                eq(LocalDate.of(2026, 12, 5)), eq(new BigDecimal("4000")), eq(new BigDecimal("9.5")),
                eq(receipt), eq(actor), anyString());
        verify(auditLogService).record(eq(1L), eq("complete"), eq("goods_receipt"), eq(20L), anyMap());
    }

    @Test
    void complete_lineWithZeroReceived_isSkipped_otherLinesReceived() {
        GoodsReceipt receipt = pendingReceipt(21L);
        RawMaterial ice = material(3L, "Đá lạnh", "kg");
        RawMaterial salt = material(4L, "Muối", "bao");
        GoodsReceiptLine iceLine = line(210L, receipt, ice, 1, "10");
        GoodsReceiptLine saltLine = line(211L, receipt, salt, 2, "5");
        when(goodsReceiptRepository.findByIdForUpdate(21L)).thenReturn(Optional.of(receipt));
        when(goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(21L)).thenReturn(List.of(iceLine, saltLine));
        when(rawMaterialRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(ice));
        when(rawMaterialRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(salt));
        when(rawMaterialStockService.receive(eq(ice), any(), any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(lot(310L, ice, "NVL-261123-01"));

        GoodsReceiptResponse done = service.complete(21L, new RawReceiptCompleteRequest(List.of(
                new RawReceiptCompleteLineRequest(210L, BigDecimal.TEN, null, null, null, new BigDecimal("4000")),
                new RawReceiptCompleteLineRequest(211L, BigDecimal.ZERO, null, null, null, null)), null), null);

        assertThat(done.status()).isEqualTo(ReceiptStatus.COMPLETED);
        assertThat(saltLine.getQtyReceived()).isEqualByComparingTo("0");
        assertThat(saltLine.getRawLot()).isNull();
        verify(rawMaterialStockService, never())
                .receive(eq(salt), any(), any(), any(), any(), any(), any(), any(), anyString());
    }

    @Test
    void complete_validatesPriceDatesAndAllLines_beforeTouchingStock() {
        GoodsReceipt receipt = pendingReceipt(22L);
        RawMaterial ice = material(3L, "Đá lạnh", "kg");
        RawMaterial salt = material(4L, "Muối", "bao");
        when(goodsReceiptRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(receipt));
        when(goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(22L))
                .thenReturn(List.of(line(220L, receipt, ice, 1, "10"), line(221L, receipt, salt, 2, "5")));
        RawReceiptCompleteLineRequest saltOk =
                new RawReceiptCompleteLineRequest(221L, BigDecimal.ONE, null, null, null, BigDecimal.TEN);

        // Thiếu đơn giá
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(220L, BigDecimal.TEN, null, null, null, null), saltOk), null),
                HttpStatus.BAD_REQUEST, "đơn giá");
        // HSD trước NSX
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(220L, BigDecimal.TEN, null,
                                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 1), BigDecimal.TEN), saltOk), null),
                HttpStatus.BAD_REQUEST, "trước ngày sản xuất");
        // HSD đã qua (hôm nay 23/11/2026 giờ VN)
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(220L, BigDecimal.TEN, null, null,
                                LocalDate.of(2026, 11, 22), BigDecimal.TEN), saltOk), null),
                HttpStatus.BAD_REQUEST, "đã qua");
        // Thiếu dòng Muối
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(220L, BigDecimal.TEN, null, null, null, BigDecimal.TEN)), null),
                HttpStatus.BAD_REQUEST, "Muối");
        // Dòng lạ
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(999L, BigDecimal.TEN, null, null, null, BigDecimal.TEN), saltOk), null),
                HttpStatus.BAD_REQUEST, "không thuộc phiếu");
        // Cả phiếu thực nhận = 0
        assertStatus(() -> service.complete(22L, complete(
                        new RawReceiptCompleteLineRequest(220L, BigDecimal.ZERO, null, null, null, null),
                        new RawReceiptCompleteLineRequest(221L, BigDecimal.ZERO, null, null, null, null)), null),
                HttpStatus.BAD_REQUEST, "hủy phiếu");

        verify(rawMaterialStockService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
    }

    @Test
    void complete_alreadyCompletedOrCancelledOrFinished_returns409() {
        GoodsReceipt completed = pendingReceipt(23L);
        completed.setStatus(ReceiptStatus.COMPLETED);
        GoodsReceipt cancelled = pendingReceipt(24L);
        cancelled.setStatus(ReceiptStatus.CANCELLED);
        GoodsReceipt finished = pendingReceipt(25L);
        finished.setReceiptType(ReceiptType.FINISHED);
        when(goodsReceiptRepository.findByIdForUpdate(23L)).thenReturn(Optional.of(completed));
        when(goodsReceiptRepository.findByIdForUpdate(24L)).thenReturn(Optional.of(cancelled));
        when(goodsReceiptRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(finished));
        RawReceiptCompleteRequest any = complete(
                new RawReceiptCompleteLineRequest(1L, BigDecimal.ONE, null, null, null, BigDecimal.ONE));

        assertStatus(() -> service.complete(23L, any, null), HttpStatus.CONFLICT, "đã nhập kho");
        assertStatus(() -> service.complete(24L, any, null), HttpStatus.CONFLICT, "đã hủy");
        assertStatus(() -> service.complete(25L, any, null), HttpStatus.CONFLICT, "nguyên liệu");
        verify(rawMaterialStockService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void complete_unknownReceipt_returns404() {
        when(goodsReceiptRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertStatus(() -> service.complete(99L, complete(
                        new RawReceiptCompleteLineRequest(1L, BigDecimal.ONE, null, null, null, BigDecimal.ONE)), null),
                HttpStatus.NOT_FOUND, "Không tìm thấy phiếu");
    }

    // ---- Hủy phiếu chờ ----

    @Test
    void cancel_pendingReceipt_marksCancelled_withReason_noStockChange() {
        GoodsReceipt receipt = pendingReceipt(30L);
        receipt.setNote("Giao sáng");
        when(goodsReceiptRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(receipt));
        when(goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(30L)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        GoodsReceiptResponse cancelled = service.cancel(30L, "  Nhà cung cấp hết hàng ", 1L);

        assertThat(cancelled.status()).isEqualTo(ReceiptStatus.CANCELLED);
        assertThat(cancelled.note()).isEqualTo("Giao sáng — Hủy: Nhà cung cấp hết hàng");
        verify(rawMaterialStockService, never()).receive(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(auditLogService).record(eq(1L), eq("cancel"), eq("goods_receipt"), eq(30L), anyMap());
    }

    @Test
    void cancel_completedReceipt_returns409() {
        GoodsReceipt receipt = pendingReceipt(31L);
        receipt.setStatus(ReceiptStatus.COMPLETED);
        when(goodsReceiptRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(receipt));

        assertStatus(() -> service.cancel(31L, null, null), HttpStatus.CONFLICT, "đã nhập kho");
    }

    // ---- Danh sách ----

    @Test
    void list_legacyRawReceiptWithoutLines_parsesNote_andIsCompleted() {
        GoodsReceipt stored = new GoodsReceipt();
        setId(stored, 9L);
        stored.setCode("PN-0001");
        stored.setReceiptType(ReceiptType.RAW);
        stored.setSource("Chợ");
        stored.setNote("Giao sáng — Đá lạnh: 12.5 kg");
        stored.setReceivedAt(Instant.parse("2026-11-06T10:00:00Z"));
        when(goodsReceiptRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored)));
        when(goodsReceiptLineRepository.findByReceipt_IdInOrderByLineNoAsc(List.of(9L))).thenReturn(List.of());

        var page = service.list(null, "raw", "completed", 1, 20);

        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo(ReceiptStatus.COMPLETED);
            assertThat(row.lines()).isEmpty();
            ReceiptItemResponse item = row.items().get(0);
            assertThat(item.name()).isEqualTo("Đá lạnh");
            assertThat(item.qty()).isEqualTo(12.5);
            assertThat(item.unit()).isEqualTo("kg");
        });
    }

    @Test
    void list_rejectsInvalidReceiptTypeOrStatus() {
        assertStatus(() -> service.list(null, "bogus", 1, 20), HttpStatus.BAD_REQUEST, "Loại phiếu không hợp lệ");
        assertStatus(() -> service.list(null, null, "bogus", 1, 20), HttpStatus.BAD_REQUEST, "Trạng thái phiếu không hợp lệ");
    }

    @Test
    void list_sortsByCode_descByDefault_ascWhenRequested() {
        when(goodsReceiptRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        service.list(null, null, null, null, 1, 20);
        service.list(null, null, null, " ASC ", 1, 20);
        service.list(null, null, null, "bogus", 1, 20);

        verify(goodsReceiptRepository, times(3)).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getAllValues()).extracting(p -> p.getSort().toString())
                .containsExactly("id: DESC", "id: ASC", "id: DESC");
    }

    @Test
    void parseRawItemsFromNote_readsSummaryAfterUserNote() {
        List<ReceiptItemResponse> items =
                GoodsReceiptService.parseRawItemsFromNote("Giao sáng — Đá lạnh: 12.5 kg; Muối: 2 bao");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("Đá lạnh");
        assertThat(items.get(0).qty()).isEqualTo(12.5);
        assertThat(items.get(1).name()).isEqualTo("Muối");
        assertThat(items.get(1).unit()).isEqualTo("bao");
    }

    // ---- helpers ----

    private void stubSaveAndFlush(long id) {
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            setId(g, id);
            return g;
        });
    }

    private static GoodsReceiptRequest rawRequest(List<RawReceiptLineRequest> lines) {
        return new GoodsReceiptRequest(ReceiptType.RAW, "Chợ", null, null, lines, null);
    }

    private static RawReceiptCompleteRequest complete(RawReceiptCompleteLineRequest... lines) {
        return new RawReceiptCompleteRequest(List.of(lines), null);
    }

    private static RawMaterial material(long id, String name, String unit) {
        RawMaterial m = new RawMaterial();
        setId(m, id);
        m.setCode("NVL-" + id);
        m.setName(name);
        m.setUnit(unit);
        m.setActive(true);
        m.setQtyOnHand(BigDecimal.ZERO);
        return m;
    }

    private static GoodsReceipt pendingReceipt(long id) {
        GoodsReceipt r = new GoodsReceipt();
        setId(r, id);
        r.setCode("PN-" + id);
        r.setReceiptType(ReceiptType.RAW);
        r.setStatus(ReceiptStatus.PENDING);
        r.setSource("Chợ");
        r.setReceivedAt(Instant.parse("2026-11-22T01:00:00Z"));
        return r;
    }

    private static GoodsReceiptLine line(long id, GoodsReceipt receipt, RawMaterial m, int no, String expected) {
        GoodsReceiptLine l = new GoodsReceiptLine();
        setId(l, id);
        l.setReceipt(receipt);
        l.setRawMaterial(m);
        l.setLineNo(no);
        l.setQtyExpected(new BigDecimal(expected));
        return l;
    }

    private static RawMaterialLot lot(long id, RawMaterial m, String code) {
        RawMaterialLot lot = new RawMaterialLot();
        setId(lot, id);
        lot.setRawMaterial(m);
        lot.setLotCode(code);
        return lot;
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status, String messagePart) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(status);
                    assertThat(api.getMessage()).contains(messagePart);
                });
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
