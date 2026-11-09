package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.FinishedReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.ReceiptItemResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;
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
    private StockService stockService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.hbsisms.feature.sales.OrderAllocationService orderAllocationService;

    private GoodsReceiptService service;
    private ProductVariant variant;
    private Location location;

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptService(
                goodsReceiptRepository,
                stockLedgerRepository,
                productVariantRepository,
                locationRepository,
                lotRepository,
                rawMaterialRepository,
                stockService,
                userRepository,
                orderAllocationService);
        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        setId(variant, 1L);
        variant.setSku("TOM-1");
        variant.setProduct(product);
        location = new Location();
        setId(location, 10L);
        location.setCode("A-01");
    }

    @Test
    void createFinished_callsReceiveInPerLineWithReceiptRef() {
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.empty());
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            setId(g, 5L);
            return g;
        });
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        // Capacity check lives in StockService; stub receiveIn for this unit test.
        when(lotRepository.findByLotCodeIgnoreCase("LOT-A")).thenReturn(Optional.empty());
        when(lotRepository.saveAndFlush(any(Lot.class))).thenAnswer(inv -> {
            Lot lot = inv.getArgument(0);
            setId(lot, 7L);
            return lot;
        });
        when(stockService.receiveIn(any())).thenReturn(new Stock());
        AppUser actor = new AppUser();
        setId(actor, 1L);
        actor.setFullName("Admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        GoodsReceiptResponse response = service.create(
                new GoodsReceiptRequest(
                        ReceiptType.FINISHED,
                        "Xưởng demo",
                        null,
                        List.of(new FinishedReceiptLineRequest(
                                1L, "LOT-A", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), 10L, 15)),
                        null),
                1L);

        assertThat(response.code()).isEqualTo("PN-0001");
        assertThat(response.supplierName()).isEqualTo("Xưởng demo");
        assertThat(response.items()).hasSize(1);

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
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            setId(g, 1L);
            return g;
        });

        assertThatThrownBy(() -> service.create(
                        new GoodsReceiptRequest(ReceiptType.FINISHED, "Nguồn", null, List.of(), null), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRaw_appendsItemSummaryAndListParsesItems() {
        when(goodsReceiptRepository.findMaxPnCode()).thenReturn(Optional.empty());
        when(goodsReceiptRepository.saveAndFlush(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            setId(g, 9L);
            return g;
        });
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
        RawMaterial material = new RawMaterial();
        setId(material, 3L);
        material.setName("Đá lạnh");
        material.setUnit("kg");
        material.setActive(true);
        material.setQtyOnHand(BigDecimal.ZERO);
        when(rawMaterialRepository.findById(3L)).thenReturn(Optional.of(material));

        GoodsReceiptResponse created = service.create(
                new GoodsReceiptRequest(
                        ReceiptType.RAW,
                        "Chợ",
                        "Giao sáng",
                        null,
                        List.of(new RawReceiptLineRequest(3L, new BigDecimal("12.5")))),
                null);

        assertThat(created.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Đá lạnh");
            assertThat(item.qty()).isEqualTo(12.5);
            assertThat(item.unit()).isEqualTo("kg");
        });
        assertThat(created.note()).contains("Đá lạnh: 12.5 kg");

        GoodsReceipt stored = new GoodsReceipt();
        setId(stored, 9L);
        stored.setCode("PN-0001");
        stored.setReceiptType(ReceiptType.RAW);
        stored.setSource("Chợ");
        stored.setNote(created.note());
        stored.setReceivedAt(Instant.parse("2026-11-06T10:00:00Z"));
        when(goodsReceiptRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stored)));

        var page = service.list(null, "raw", 1, 20);
        assertThat(page.items()).singleElement().satisfies(row -> {
            assertThat(row.items()).hasSize(1);
            ReceiptItemResponse item = row.items().get(0);
            assertThat(item.name()).isEqualTo("Đá lạnh");
            assertThat(item.qty()).isEqualTo(12.5);
            assertThat(item.unit()).isEqualTo("kg");
        });
    }

    @Test
    void list_rejectsInvalidReceiptType() {
        assertThatThrownBy(() -> service.list(null, "bogus", 1, 20))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("Loại phiếu không hợp lệ");
                });
    }

    @Test
    void parseRawItemsFromNote_readsSummaryAfterUserNote() {
        List<ReceiptItemResponse> items =
                GoodsReceiptService.parseRawItemsFromNote("Giao sáng — Đá lạnh: 12.5 kg; Muối: 2 bao");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).name()).isEqualTo("Đá lạnh");
        assertThat(items.get(0).qty()).isEqualTo(12.5);
        assertThat(items.get(0).unit()).isEqualTo("kg");
        assertThat(items.get(1).name()).isEqualTo("Muối");
        assertThat(items.get(1).qty()).isEqualTo(2.0);
        assertThat(items.get(1).unit()).isEqualTo("bao");
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
