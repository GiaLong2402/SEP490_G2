package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockLedgerRepository stockLedgerRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private LotRepository lotRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private UserRepository userRepository;

    private StockService stockService;
    private ProductVariant variant;
    private Lot lot;
    private Location locationA;
    private Location locationB;

    @BeforeEach
    void setUp() {
        stockService = new StockService(
                stockRepository,
                stockLedgerRepository,
                productVariantRepository,
                lotRepository,
                locationRepository,
                userRepository);
        Product product = new Product();
        product.setName("Tôm sú");
        variant = new ProductVariant();
        setId(variant, 1L);
        variant.setSku("TOM-SU-05");
        variant.setProduct(product);
        lot = new Lot();
        setId(lot, 10L);
        lot.setLotCode("LOT-1");
        lot.setVariant(variant);
        Warehouse warehouse = new Warehouse();
        setId(warehouse, 1L);
        warehouse.setCode("KHO-1");
        warehouse.setCapacity(500);
        locationA = new Location();
        setId(locationA, 100L);
        locationA.setCode("A-01");
        locationA.setCapacity(100);
        locationA.setWarehouse(warehouse);
        locationB = new Location();
        setId(locationB, 101L);
        locationB.setCode("A-02");
        locationB.setCapacity(100);
        locationB.setWarehouse(warehouse);
    }

    @Test
    void issueOut_conflictsWhenAtomicUpdateReturnsZero() {
        Stock stock = stock(1L, 5);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock));
        when(stockRepository.adjustQtyAvailable(1L, -3)).thenReturn(0);

        assertThatThrownBy(() -> stockService.issueOut(StockMutation.of(1L, 10L, 100L, 3, null, null, null, 1L)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void receiveIn_createsStockAndWritesLedger() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.empty());
        when(productVariantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(lotRepository.findById(10L)).thenReturn(Optional.of(lot));
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(0L);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(0L);
        when(stockRepository.saveAndFlush(any(Stock.class))).thenAnswer(inv -> {
            Stock s = inv.getArgument(0);
            setId(s, 50L);
            return s;
        });
        when(stockRepository.adjustQtyAvailable(50L, 20)).thenReturn(1);
        when(stockRepository.findById(50L)).thenReturn(Optional.of(stock(50L, 20)));

        stockService.receiveIn(StockMutation.of(1L, 10L, 100L, 20, "goods_receipt", null, "Nhập", 1L));

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.IN);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(20);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(20);
    }

    @Test
    void receiveIn_conflictsWhenExceedsLocationCapacity() {
        locationA.setCapacity(20);
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(0L);

        assertThatThrownBy(
                        () -> stockService.receiveIn(
                                StockMutation.of(1L, 10L, 100L, 30, "goods_receipt", null, "Nhập", 1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage())
                            .isEqualTo("Vị trí A-01 sức chứa 20 khay, đang 0, không nhận thêm 30 (vượt 10).");
                });
        verify(stockLedgerRepository, never()).save(any());
        verify(stockRepository, never()).sumQtyByWarehouseId(anyLong());
    }

    @Test
    void receiveIn_conflictsWhenExceedsWarehouseCapacity() {
        locationA.setCapacity(100);
        locationA.getWarehouse().setCapacity(25);
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(0L);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(20L);

        assertThatThrownBy(
                        () -> stockService.receiveIn(
                                StockMutation.of(1L, 10L, 100L, 10, "goods_receipt", null, "Nhập", 1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage())
                            .isEqualTo("Kho KHO-1 sức chứa 25 khay, đang 20, không nhận thêm 10 (vượt 5).");
                });
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_conflictsWhenSourceLacksQty() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 2)));
        when(locationRepository.findById(101L)).thenReturn(Optional.of(locationB));
        when(stockRepository.sumQtyByLocationId(101L)).thenReturn(0L);
        when(stockRepository.adjustQtyAvailable(1L, -5)).thenReturn(0);

        assertThatThrownBy(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 5, null, 1L)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_updatesSourceAndTarget() {
        Stock source = stock(1L, 10);
        Stock target = stock(2L, 0);
        target.setLocation(locationB);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 101L))
                .thenReturn(Optional.of(target));
        when(stockRepository.adjustQtyAvailable(1L, -4)).thenReturn(1);
        when(stockRepository.adjustQtyAvailable(2L, 4)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 6)));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(stock(2L, 4)));
        when(locationRepository.findById(101L)).thenReturn(Optional.of(locationB));
        when(stockRepository.sumQtyByLocationId(101L)).thenReturn(0L);

        stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 4, "Chuyển", 1L));

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.MOVE);
        assertThat(captor.getValue().getToLocation()).isEqualTo(locationB);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(4);
    }

    @Test
    void adjust_requiresNote() {
        assertThatThrownBy(() -> stockService.adjust(StockMutation.of(1L, 10L, 100L, 1, null, null, "  ", 1L), 1))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    @Test
    void hold_reducesAvailableAndIncreasesHeld() {
        Stock stock = stock(1L, 8);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock));
        when(stockRepository.adjustQtyAvailable(1L, -2)).thenReturn(1);
        when(stockRepository.adjustQtyHeld(1L, 2)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 6)));

        stockService.hold(StockMutation.of(1L, 10L, 100L, 2, "sales_order_line", 9L, "Giữ", 1L));

        verify(stockRepository).adjustQtyAvailable(eq(1L), eq(-2));
        verify(stockRepository).adjustQtyHeld(eq(1L), eq(2));
        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.HOLD);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(-2);
    }

    private Stock stock(long id, int available) {
        Stock stock = new Stock();
        setId(stock, id);
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(locationA);
        stock.setQtyAvailable(available);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        return stock;
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
