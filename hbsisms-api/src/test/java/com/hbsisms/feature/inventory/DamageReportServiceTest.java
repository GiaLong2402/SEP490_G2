package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.DamageReportRequest;
import com.hbsisms.feature.inventory.dto.DamageReportResponse;
import com.hbsisms.feature.user.AppUser;
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
class DamageReportServiceTest {

    @Mock
    private DamageReportRepository damageReportRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private LotRepository lotRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private StockService stockService;

    private DamageReportService service;
    private ProductVariant variant;
    private Lot lot;
    private Location location;
    private Stock stock;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new DamageReportService(
                damageReportRepository,
                stockRepository,
                productVariantRepository,
                lotRepository,
                locationRepository,
                warehouseRepository,
                userRepository,
                approvalTaskRepository,
                stockService);

        Product product = new Product();
        product.setName("Tôm");
        variant = new ProductVariant();
        setId(variant, 1L);
        variant.setSku("TOM-1");
        variant.setProduct(product);

        lot = new Lot();
        setId(lot, 7L);
        lot.setLotCode("LOT-A");
        lot.setVariant(variant);

        Warehouse warehouse = new Warehouse();
        setId(warehouse, 1L);
        warehouse.setCode("KHO-1");
        location = new Location();
        setId(location, 10L);
        location.setCode("A-01");
        location.setWarehouse(warehouse);

        stock = new Stock();
        setId(stock, 99L);
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(8);
        stock.setQtyQuarantine(2);
        stock.setQtyExpired(0);

        actor = new AppUser();
        setId(actor, 3L);
        actor.setFullName("Kho");
    }

    @Test
    void create_opensPendingApprovalWithoutDeductingStock() {
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(lotRepository.findById(7L)).thenReturn(Optional.of(lot));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 7L, 10L)).thenReturn(Optional.of(stock));
        when(userRepository.findById(3L)).thenReturn(Optional.of(actor));
        when(damageReportRepository.findMaxBhCode()).thenReturn(Optional.empty());
        when(damageReportRepository.saveAndFlush(any(DamageReport.class))).thenAnswer(inv -> {
            DamageReport r = inv.getArgument(0);
            setId(r, 50L);
            return r;
        });
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(DamageReportService.REF_TYPE), eq(50L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        when(warehouseRepository.count()).thenReturn(1L);
        when(damageReportRepository.findDetailedById(50L)).thenAnswer(inv -> {
            DamageReport r = new DamageReport();
            setId(r, 50L);
            r.setCode("BH-0001");
            r.setVariant(variant);
            r.setLot(lot);
            r.setLocation(location);
            r.setQty(5);
            r.setReason("Vỡ bao");
            r.setStatus(ApprovalStatus.PENDING);
            r.setReportedBy(actor);
            return Optional.of(r);
        });

        DamageReportResponse response = service.create(
                new DamageReportRequest(1L, 7L, 10L, 5, "Vỡ bao", null), 3L);

        assertThat(response.code()).isEqualTo("BH-0001");
        assertThat(response.status()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(response.locationId()).isEqualTo(10L);

        ArgumentCaptor<ApprovalTask> taskCaptor = ArgumentCaptor.forClass(ApprovalTask.class);
        verify(approvalTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getApprovalType()).isEqualTo(ApprovalType.DAMAGE_REPORT);
        assertThat(taskCaptor.getValue().getRefType()).isEqualTo(DamageReportService.REF_TYPE);
        assertThat(taskCaptor.getValue().getRefId()).isEqualTo(50L);
        verify(stockService, never()).damage(any());
    }

    @Test
    void create_rejectsWhenQtyExceedsOnHand() {
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(lotRepository.findById(7L)).thenReturn(Optional.of(lot));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 7L, 10L)).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> service.create(
                        new DamageReportRequest(1L, 7L, 10L, 20, "Hỏng", null), 3L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockService, never()).damage(any());
    }

    @Test
    void applyDecision_approved_callsStockDamage() {
        DamageReport report = new DamageReport();
        setId(report, 50L);
        report.setCode("BH-0001");
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(location);
        report.setQty(5);
        report.setReason("Vỡ bao");
        report.setStatus(ApprovalStatus.PENDING);
        when(damageReportRepository.findDetailedById(50L)).thenReturn(Optional.of(report));
        when(damageReportRepository.save(report)).thenReturn(report);
        when(stockService.damage(any())).thenReturn(stock);

        service.applyDecision(50L, true, actor, "OK");

        assertThat(report.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        ArgumentCaptor<StockMutation> captor = ArgumentCaptor.forClass(StockMutation.class);
        verify(stockService).damage(captor.capture());
        assertThat(captor.getValue().qty()).isEqualTo(5);
        assertThat(captor.getValue().refType()).isEqualTo(DamageReportService.REF_TYPE);
        assertThat(captor.getValue().refId()).isEqualTo(50L);
    }

    @Test
    void applyDecision_rejected_doesNotTouchStock() {
        DamageReport report = new DamageReport();
        setId(report, 50L);
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(location);
        report.setQty(5);
        report.setReason("Vỡ bao");
        report.setStatus(ApprovalStatus.PENDING);
        when(damageReportRepository.findDetailedById(50L)).thenReturn(Optional.of(report));
        when(damageReportRepository.save(report)).thenReturn(report);

        service.applyDecision(50L, false, actor, "Không đủ bằng chứng");

        assertThat(report.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verify(stockService, never()).damage(any());
    }

    @Test
    void applyDecision_whenAlreadyDecided_rejectsWithoutTouchingStock() {
        DamageReport report = new DamageReport();
        setId(report, 50L);
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(location);
        report.setQty(5);
        report.setReason("Vỡ bao");
        report.setStatus(ApprovalStatus.APPROVED);
        when(damageReportRepository.findDetailedById(50L)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.applyDecision(50L, true, actor, "lần 2"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockService, never()).damage(any());
        verify(damageReportRepository, never()).save(any());
    }

    @Test
    void applyDecision_approved_propagatesWhenStockDamageFails() {
        DamageReport report = new DamageReport();
        setId(report, 50L);
        report.setCode("BH-0001");
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(location);
        report.setQty(5);
        report.setReason("Vỡ bao");
        report.setStatus(ApprovalStatus.PENDING);
        when(damageReportRepository.findDetailedById(50L)).thenReturn(Optional.of(report));
        when(damageReportRepository.save(report)).thenReturn(report);
        when(stockService.damage(any()))
                .thenThrow(ApiException.conflict("Không đủ tồn kho để trừ hỏng"));

        assertThatThrownBy(() -> service.applyDecision(50L, true, actor, "OK"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockService, times(1)).damage(any());
    }

    @Test
    void create_rejectsWhenLotNotBelongToVariant() {
        ProductVariant other = new ProductVariant();
        setId(other, 2L);
        Lot foreignLot = new Lot();
        setId(foreignLot, 8L);
        foreignLot.setLotCode("LOT-B");
        foreignLot.setVariant(other);

        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(lotRepository.findById(8L)).thenReturn(Optional.of(foreignLot));

        assertThatThrownBy(() -> service.create(
                        new DamageReportRequest(1L, 8L, 10L, 1, "Hỏng", null), 3L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockService, never()).damage(any());
    }

    @Test
    void create_rejectsWhenStockMissingAtLocation() {
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.of(variant));
        when(lotRepository.findById(7L)).thenReturn(Optional.of(lot));
        when(locationRepository.findById(10L)).thenReturn(Optional.of(location));
        when(stockRepository.findByVariant_IdAndLot_IdAndLocation_Id(1L, 7L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                        new DamageReportRequest(1L, 7L, 10L, 1, "Hỏng", null), 3L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockService, never()).damage(any());
    }

    @Test
    void create_rejectsWhenReasonBlank() {
        assertThatThrownBy(() -> service.create(
                        new DamageReportRequest(1L, 7L, 10L, 1, "   ", null), 3L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stockService, never()).damage(any());
    }

    @Test
    void create_rejectsWhenVariantNotFound() {
        when(productVariantRepository.findWithProductById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                        new DamageReportRequest(1L, 7L, 10L, 1, "Hỏng", null), 3L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void applyDecision_notFound() {
        when(damageReportRepository.findDetailedById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDecision(999L, true, actor, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(stockService, never()).damage(any());
    }

    @Test
    void applyDecision_approved_rejectsWhenLocationMissing() {
        DamageReport report = new DamageReport();
        setId(report, 50L);
        report.setVariant(variant);
        report.setLot(lot);
        report.setLocation(null);
        report.setQty(5);
        report.setReason("Vỡ bao");
        report.setStatus(ApprovalStatus.PENDING);
        when(damageReportRepository.findDetailedById(50L)).thenReturn(Optional.of(report));
        when(damageReportRepository.save(report)).thenReturn(report);

        assertThatThrownBy(() -> service.applyDecision(50L, true, actor, "OK"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(stockService, never()).damage(any());
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
