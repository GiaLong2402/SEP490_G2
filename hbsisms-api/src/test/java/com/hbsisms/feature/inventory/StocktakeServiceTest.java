package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.inventory.dto.StocktakeCompleteResponse;
import com.hbsisms.feature.inventory.dto.StocktakeCountItem;
import com.hbsisms.feature.inventory.dto.StocktakeDetailResponse;
import com.hbsisms.feature.inventory.dto.StocktakeInput;
import com.hbsisms.feature.inventory.dto.StocktakeSaveCountsRequest;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {

    @Mock
    private StocktakeRepository stocktakeRepository;
    @Mock
    private StocktakeLineRepository stocktakeLineRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private StockService stockService;
    @Mock
    private UserRepository userRepository;

    private StocktakeService service;
    private AppUser actor;
    private Warehouse warehouse;
    private Location location;
    private ProductVariant variant;
    private Lot lot;
    private Stock stock;

    @BeforeEach
    void setUp() {
        service = new StocktakeService(
                stocktakeRepository,
                stocktakeLineRepository,
                stockRepository,
                locationRepository,
                warehouseRepository,
                approvalTaskRepository,
                stockService,
                userRepository);

        actor = new AppUser();
        ReflectionTestUtils.setField(actor, "id", 1L);
        actor.setFullName("Kho");

        warehouse = new Warehouse();
        ReflectionTestUtils.setField(warehouse, "id", 1L);
        warehouse.setCode("KHO-1");
        warehouse.setActive(true);

        location = new Location();
        ReflectionTestUtils.setField(location, "id", 10L);
        location.setCode("A-01");
        location.setZone("Kho lạnh");
        location.setWarehouse(warehouse);

        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 5L);
        variant.setSku("TOM-1");
        variant.setProduct(product);

        lot = new Lot();
        ReflectionTestUtils.setField(lot, "id", 7L);
        lot.setLotCode("LOT-A");
        lot.setVariant(variant);

        stock = new Stock();
        ReflectionTestUtils.setField(stock, "id", 99L);
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(10);
        stock.setQtyHeld(2);
    }

    @Test
    void create_rejectsWhenNoStockInScope() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(locationRepository.findAllWithWarehouse()).thenReturn(List.of(location));
        when(stockRepository.findAllWithDetails()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new StocktakeInput("Kho lạnh", null), 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_snapshotsSystemQtyFromAvailablePlusHeld() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));
        when(locationRepository.findAllWithWarehouse()).thenReturn(List.of(location));
        when(stockRepository.findAllWithDetails()).thenReturn(List.of(stock));
        when(stocktakeRepository.findMaxKkCode()).thenReturn(Optional.empty());
        when(stocktakeRepository.saveAndFlush(any(Stocktake.class))).thenAnswer(inv -> {
            Stocktake s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 40L);
            return s;
        });
        when(stocktakeRepository.save(any(Stocktake.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stocktakeRepository.findWithActorsById(40L)).thenAnswer(inv -> Optional.of(countingStocktake(40L)));
        when(stocktakeLineRepository.findDetailedByStocktakeId(40L)).thenAnswer(inv -> {
            Stocktake parent = countingStocktake(40L);
            StocktakeLine line = line(100L, 12, null);
            line.setStocktake(parent);
            return List.of(line);
        });
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(warehouseRepository.count()).thenReturn(1L);

        StocktakeDetailResponse detail = service.create(new StocktakeInput("Kho lạnh", null), 1L);

        assertThat(detail.code()).isEqualTo("KK-0001");
        assertThat(detail.lines()).hasSize(1);
        assertThat(detail.lines().get(0).systemQty()).isEqualTo(12);

        ArgumentCaptor<Stocktake> captor = ArgumentCaptor.forClass(Stocktake.class);
        verify(stocktakeRepository).save(captor.capture());
        assertThat(captor.getValue().getLines()).hasSize(1);
        assertThat(captor.getValue().getLines().get(0).getSystemQty()).isEqualTo(12);
        assertThat(captor.getValue().getStatus()).isEqualTo(StocktakeStatus.COUNTING);
    }

    @Test
    void complete_whenNoDiff_marksDoneWithoutApprovalOrAdjust() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine line = line(100L, 12, 12);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(line));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        StocktakeCompleteResponse response = service.complete(40L, 1L);

        assertThat(response.needsApproval()).isFalse();
        assertThat(stocktake.getStatus()).isEqualTo(StocktakeStatus.DONE);
        verify(approvalTaskRepository, never()).save(any(ApprovalTask.class));
        verify(stockService, never()).adjustByStockId(anyLong(), anyInt(), any(), any());
    }

    @Test
    void complete_whenDiff_opensApprovalWithoutAdjustingYet() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine line = line(100L, 12, 10);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(line));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        StocktakeCompleteResponse response = service.complete(40L, 1L);

        assertThat(response.needsApproval()).isTrue();
        assertThat(stocktake.getStatus()).isEqualTo(StocktakeStatus.COUNTING);
        ArgumentCaptor<ApprovalTask> taskCaptor = ArgumentCaptor.forClass(ApprovalTask.class);
        verify(approvalTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getApprovalType()).isEqualTo(ApprovalType.STOCKTAKE);
        assertThat(taskCaptor.getValue().getRefId()).isEqualTo(40L);
        verify(stockService, never()).adjustByStockId(anyLong(), anyInt(), any(), any());
    }

    @Test
    void applyApprovalDecision_approved_adjustsByCountedMinusSystem() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine line = line(100L, 12, 9);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(line));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(5L, 7L, 10L)).thenReturn(Optional.of(stock));

        service.applyApprovalDecision(40L, true, 1L);

        assertThat(stocktake.getStatus()).isEqualTo(StocktakeStatus.DONE);
        verify(stockService).adjustByStockId(eq(99L), eq(-3), any(), eq(1L));
    }

    @Test
    void applyApprovalDecision_approved_skipsZeroDiffLines() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine same = line(100L, 12, 12);
        StocktakeLine diff = line(101L, 12, 15);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(same, diff));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(5L, 7L, 10L)).thenReturn(Optional.of(stock));

        service.applyApprovalDecision(40L, true, 1L);

        verify(stockService, times(1)).adjustByStockId(eq(99L), eq(3), any(), eq(1L));
    }

    @Test
    void applyApprovalDecision_rejected_keepsCountingWithoutAdjust() {
        Stocktake stocktake = countingStocktake(40L);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));

        service.applyApprovalDecision(40L, false, 1L);

        assertThat(stocktake.getStatus()).isEqualTo(StocktakeStatus.COUNTING);
        verify(stockService, never()).adjustByStockId(anyLong(), anyInt(), any(), any());
        verify(stocktakeLineRepository, never()).findByStocktake_Id(anyLong());
    }

    @Test
    void cancel_marksCancelled() {
        Stocktake stocktake = countingStocktake(40L);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        service.cancel(40L, 1L);

        assertThat(stocktake.getStatus()).isEqualTo(StocktakeStatus.CANCELLED);
        assertThat(stocktake.getClosedAt()).isNotNull();
        verify(stockService, never()).adjustByStockId(anyLong(), anyInt(), any(), any());
    }

    @Test
    void saveCounts_rejectsNegativeCountedQty() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine line = line(100L, 12, null);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(line));
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.saveCounts(
                        40L, new StocktakeSaveCountsRequest(List.of(new StocktakeCountItem(100L, -1))), 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void complete_rejectsWhenUncountedLinesRemain() {
        Stocktake stocktake = countingStocktake(40L);
        StocktakeLine line = line(100L, 12, null);
        when(stocktakeRepository.findWithActorsById(40L)).thenReturn(Optional.of(stocktake));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(StocktakeService.REF_TYPE), eq(40L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(stocktakeLineRepository.findByStocktake_Id(40L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.complete(40L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private Stocktake countingStocktake(long id) {
        Stocktake stocktake = new Stocktake();
        ReflectionTestUtils.setField(stocktake, "id", id);
        stocktake.setCode("KK-0001");
        stocktake.setScope("Kho lạnh");
        stocktake.setStatus(StocktakeStatus.COUNTING);
        stocktake.setCreatedBy(actor);
        stocktake.setCountedBy(actor);
        return stocktake;
    }

    private StocktakeLine line(long id, int systemQty, Integer countedQty) {
        StocktakeLine line = new StocktakeLine();
        ReflectionTestUtils.setField(line, "id", id);
        line.setVariant(variant);
        line.setLot(lot);
        line.setLocation(location);
        line.setSystemQty(systemQty);
        line.setCountedQty(countedQty);
        return line;
    }
}
