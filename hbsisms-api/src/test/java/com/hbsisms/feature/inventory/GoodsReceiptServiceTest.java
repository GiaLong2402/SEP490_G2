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
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
                userRepository);
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
