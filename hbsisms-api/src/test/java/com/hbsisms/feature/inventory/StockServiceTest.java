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
    private Location locationOtherWarehouse;

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
        Warehouse warehouse2 = new Warehouse();
        setId(warehouse2, 2L);
        warehouse2.setCode("KHO-2");
        warehouse2.setCapacity(30);
        locationOtherWarehouse = new Location();
        setId(locationOtherWarehouse, 200L);
        locationOtherWarehouse.setCode("B-01");
        locationOtherWarehouse.setCapacity(100);
        locationOtherWarehouse.setWarehouse(warehouse2);
    }

    // --- validation: qty <= 0 ---

    @Test
    void receiveIn_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.receiveIn(mutation(0)));
        assertBadRequest(() -> stockService.receiveIn(mutation(-1)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void issueOut_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.issueOut(mutation(0)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void hold_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.hold(mutation(0)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void release_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.release(mutation(0)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 0, null, 1L)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void damage_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.damage(mutation(0)));
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void expire_rejectsNonPositiveQty() {
        assertBadRequest(() -> stockService.expire(mutation(0)));
        verify(stockLedgerRepository, never()).save(any());
    }

    // --- IN ---

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
    void receiveIn_addsToExistingStock() {
        Stock existing = stock(1L, 5);
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(5L);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(5L);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(existing));
        when(stockRepository.adjustQtyAvailable(1L, 10)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 15)));

        stockService.receiveIn(StockMutation.of(1L, 10L, 100L, 10, "goods_receipt", 7L, "Nhập thêm", 1L));

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.IN);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(10);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(15);
        verify(stockRepository, never()).saveAndFlush(any());
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

    // --- OUT ---

    @Test
    void issueOut_writesLedgerWhenSuccessful() {
        Stock stock = stock(1L, 10);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock));
        when(stockRepository.adjustQtyAvailable(1L, -3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 7)));

        stockService.issueOut(StockMutation.of(1L, 10L, 100L, 3, "sales", 1L, "Xuất", 1L));

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.OUT);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(-3);
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(7);
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
    void issueOut_notFoundWhenStockMissing() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.issueOut(mutation(2)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(stockLedgerRepository, never()).save(any());
    }

    // --- HOLD / RELEASE ---

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

    @Test
    void hold_conflictsWhenAvailableInsufficient() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 1)));
        when(stockRepository.adjustQtyAvailable(1L, -5)).thenReturn(0);

        assertThatThrownBy(() -> stockService.hold(mutation(5)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
        verify(stockRepository, never()).adjustQtyHeld(anyLong(), anyInt());
    }

    @Test
    void release_returnsHeldToAvailableAndWritesLedger() {
        Stock stock = stock(1L, 6);
        stock.setQtyHeld(3);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock));
        when(stockRepository.adjustQtyHeld(1L, -2)).thenReturn(1);
        when(stockRepository.adjustQtyAvailable(1L, 2)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 8)));

        stockService.release(StockMutation.of(1L, 10L, 100L, 2, "sales_order_line", 9L, "Nhả", 1L));

        verify(stockRepository).adjustQtyHeld(1L, -2);
        verify(stockRepository).adjustQtyAvailable(1L, 2);
        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.RELEASE);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(2);
    }

    @Test
    void release_conflictsWhenHeldInsufficient() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 5)));
        when(stockRepository.adjustQtyHeld(1L, -3)).thenReturn(0);

        assertThatThrownBy(() -> stockService.release(mutation(3)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).isEqualTo("Không đủ tồn đang giữ");
                });
        verify(stockLedgerRepository, never()).save(any());
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    // --- MOVE ---

    @Test
    void move_requiresToLocationId() {
        assertThatThrownBy(() -> stockService.move(mutation(2)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).isEqualTo("Vị trí đích là bắt buộc");
                });
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_rejectsSameLocation() {
        assertThatThrownBy(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 100L, 2, null, 1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).isEqualTo("Vị trí đích trùng vị trí hiện tại");
                });
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_notFoundWhenTargetLocationMissing() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 5)));
        when(locationRepository.findById(101L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 2, null, 1L)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void move_conflictsWhenTargetLocationCapacityExceeded() {
        locationB.setCapacity(5);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 10)));
        when(locationRepository.findById(101L)).thenReturn(Optional.of(locationB));
        when(stockRepository.sumQtyByLocationId(101L)).thenReturn(3L);

        assertThatThrownBy(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 4, null, 1L)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    @Test
    void move_sameWarehouse_doesNotCheckWarehouseCapacityAgain() {
        Stock source = stock(1L, 10);
        Stock target = stock(2L, 0);
        target.setLocation(locationB);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 101L))
                .thenReturn(Optional.of(target));
        when(locationRepository.findById(101L)).thenReturn(Optional.of(locationB));
        when(stockRepository.sumQtyByLocationId(101L)).thenReturn(0L);
        when(stockRepository.adjustQtyAvailable(1L, -4)).thenReturn(1);
        when(stockRepository.adjustQtyAvailable(2L, 4)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 6)));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(stock(2L, 4)));

        stockService.move(StockMutation.move(1L, 10L, 100L, 101L, 4, "Chuyển", 1L));

        verify(stockRepository, never()).sumQtyByWarehouseId(anyLong());
        verify(stockLedgerRepository).save(any());
    }

    @Test
    void move_crossWarehouse_conflictsWhenTargetWarehouseFull() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 20)));
        when(locationRepository.findById(200L)).thenReturn(Optional.of(locationOtherWarehouse));
        when(stockRepository.sumQtyByLocationId(200L)).thenReturn(0L);
        when(stockRepository.sumQtyByWarehouseId(2L)).thenReturn(28L);

        assertThatThrownBy(() -> stockService.move(StockMutation.move(1L, 10L, 100L, 200L, 5, null, 1L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("KHO-2");
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
    void moveByStockId_notFound() {
        when(stockRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.moveByStockId(99L, 101L, 2, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void moveByStockId_delegatesToMove() {
        Stock source = stock(1L, 10);
        Stock target = stock(2L, 0);
        target.setLocation(locationB);
        when(stockRepository.findWithDetailsById(1L)).thenReturn(Optional.of(source));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 101L))
                .thenReturn(Optional.of(target));
        when(locationRepository.findById(101L)).thenReturn(Optional.of(locationB));
        when(stockRepository.sumQtyByLocationId(101L)).thenReturn(0L);
        when(stockRepository.adjustQtyAvailable(1L, -3)).thenReturn(1);
        when(stockRepository.adjustQtyAvailable(2L, 3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 7)));
        when(stockRepository.findById(2L)).thenReturn(Optional.of(stock(2L, 3)));

        stockService.moveByStockId(1L, 101L, 3, 1L);

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.MOVE);
        assertThat(captor.getValue().getNote()).isEqualTo("Chuyển vị trí");
    }

    // --- ADJUST ---

    @Test
    void adjust_requiresNote() {
        assertThatThrownBy(() -> stockService.adjust(StockMutation.of(1L, 10L, 100L, 1, null, null, "  ", 1L), 1))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    @Test
    void adjust_rejectsZeroDelta() {
        assertThatThrownBy(() -> stockService.adjust(StockMutation.of(1L, 10L, 100L, 1, null, null, "Lý do", 1L), 0))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    @Test
    void adjust_positiveChecksCapacityAndWritesLedger() {
        Stock existing = stock(1L, 5);
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(5L);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(5L);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(existing));
        when(stockRepository.adjustQtyAvailable(1L, 3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 8)));

        stockService.adjust(StockMutation.of(1L, 10L, 100L, 3, null, null, "Kiểm kê thừa", 1L), 3);

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.ADJUST);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(3);
    }

    @Test
    void adjust_negativeConflictsWhenInsufficient() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 2)));
        when(stockRepository.adjustQtyAvailable(1L, -5)).thenReturn(0);

        assertThatThrownBy(
                        () -> stockService.adjust(StockMutation.of(1L, 10L, 100L, 5, null, null, "Hao", 1L), -5))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void adjustByStockId_notFound() {
        when(stockRepository.findWithDetailsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.adjustByStockId(99L, -1, "Lý do", 1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getMessage()).isEqualTo("Không tìm thấy dòng tồn");
                });
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void adjustByStockId_zeroDelta_rejected() {
        when(stockRepository.findWithDetailsById(1L)).thenReturn(Optional.of(stock(1L, 5)));

        assertThatThrownBy(() -> stockService.adjustByStockId(1L, 0, "Không đổi", 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockRepository, never()).adjustQtyAvailable(anyLong(), anyInt());
    }

    @Test
    void adjustByStockId_decreasesAndWritesLedger() {
        Stock source = stock(1L, 10);
        when(stockRepository.findWithDetailsById(1L)).thenReturn(Optional.of(source));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.adjustQtyAvailable(1L, -2)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 8)));

        stockService.adjustByStockId(1L, -2, "Hao hụt", 1L);

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.ADJUST);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(-2);
        assertThat(captor.getValue().getNote()).isEqualTo("Hao hụt");
    }

    @Test
    void adjustByStockId_increasesAndWritesLedger() {
        Stock source = stock(1L, 5);
        when(stockRepository.findWithDetailsById(1L)).thenReturn(Optional.of(source));
        when(locationRepository.findById(100L)).thenReturn(Optional.of(locationA));
        when(stockRepository.sumQtyByLocationId(100L)).thenReturn(5L);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(5L);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.adjustQtyAvailable(1L, 3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 8)));

        stockService.adjustByStockId(1L, 3, "Bù thiếu đếm", 1L);

        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.ADJUST);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(3);
        assertThat(captor.getValue().getNote()).isEqualTo("Bù thiếu đếm");
    }

    // --- DAMAGE / EXPIRE ---

    @Test
    void damage_deductsAcrossBucketsAndWritesLedger() {
        Stock source = stock(1L, 5);
        source.setQtyQuarantine(2);
        source.setQtyExpired(1);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));
        when(stockRepository.adjustQtyQuarantine(1L, -2)).thenReturn(1);
        when(stockRepository.adjustQtyAvailable(1L, -3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 2)));

        stockService.damage(StockMutation.of(1L, 10L, 100L, 5, "damage_report", 1L, "Hỏng", 1L));

        verify(stockRepository).adjustQtyQuarantine(1L, -2);
        verify(stockRepository).adjustQtyAvailable(1L, -3);
        verify(stockRepository, never()).adjustQtyExpired(anyLong(), anyInt());
        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.DAMAGE);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(-5);
    }

    @Test
    void damage_conflictsWhenOnHandInsufficient() {
        Stock source = stock(1L, 1);
        source.setQtyQuarantine(0);
        source.setQtyExpired(0);
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> stockService.damage(mutation(5)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).isEqualTo("Không đủ tồn kho để trừ hỏng");
                });
        verify(stockLedgerRepository, never()).save(any());
    }

    @Test
    void expire_movesAvailableToExpiredAndWritesLedger() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 8)));
        when(stockRepository.adjustQtyAvailable(1L, -3)).thenReturn(1);
        when(stockRepository.adjustQtyExpired(1L, 3)).thenReturn(1);
        when(stockRepository.findById(1L)).thenReturn(Optional.of(stock(1L, 5)));

        stockService.expire(StockMutation.of(1L, 10L, 100L, 3, "expire_job", null, "Hết hạn", 1L));

        verify(stockRepository).adjustQtyAvailable(1L, -3);
        verify(stockRepository).adjustQtyExpired(1L, 3);
        ArgumentCaptor<StockLedger> captor = ArgumentCaptor.forClass(StockLedger.class);
        verify(stockLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.EXPIRE);
        assertThat(captor.getValue().getQtyDelta()).isEqualTo(-3);
    }

    @Test
    void expire_conflictsWhenAvailableInsufficient() {
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 10L, 100L))
                .thenReturn(Optional.of(stock(1L, 1)));
        when(stockRepository.adjustQtyAvailable(1L, -5)).thenReturn(0);

        assertThatThrownBy(() -> stockService.expire(mutation(5)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockLedgerRepository, never()).save(any());
        verify(stockRepository, never()).adjustQtyExpired(anyLong(), anyInt());
    }

    private StockMutation mutation(int qty) {
        return StockMutation.of(1L, 10L, 100L, qty, null, null, null, 1L);
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
