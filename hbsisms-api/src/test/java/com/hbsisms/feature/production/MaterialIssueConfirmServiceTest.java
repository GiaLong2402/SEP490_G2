package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.RawMaterialStockService;
import com.hbsisms.feature.inventory.RawMaterialStockService.IssueSlice;
import com.hbsisms.feature.production.dto.MaterialIssueConfirmRequest;
import com.hbsisms.feature.production.dto.MaterialIssueResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
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

/** Cường — Inter 1 task 50: xác nhận xuất NVL. */
@ExtendWith(MockitoExtension.class)
class MaterialIssueConfirmServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Mock
    private MaterialIssueRepository materialIssueRepository;
    @Mock
    private MaterialIssueLineRepository materialIssueLineRepository;
    @Mock
    private ProductionOrderRepository productionOrderRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private RawMaterialStockService rawMaterialStockService;
    @Mock
    private BusinessCodeService businessCodeService;
    @Mock
    private UserRepository userRepository;

    private MaterialIssueService service;

    @BeforeEach
    void setUp() {
        service = new MaterialIssueService(
                materialIssueRepository,
                materialIssueLineRepository,
                productionOrderRepository,
                rawMaterialRepository,
                rawMaterialStockService,
                businessCodeService,
                userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void confirm_autoFefo_marksCompletedAndInProgress() {
        ProductionOrder order = order(1L, ProductionOrderStatus.APPROVED);
        MaterialIssue issue = pendingIssue(5L, order);
        RawMaterial raw = raw(9L);
        MaterialIssueLine required = requiredLine(raw, "4");
        when(materialIssueRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(issue));
        when(productionOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L))
                .thenReturn(List.of(required))
                .thenReturn(List.of()); // after replace → toResponse dùng issue.lines
        when(rawMaterialRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(raw));
        RawMaterialLot lot = lot(raw, 20L, "L-1");
        when(rawMaterialStockService.issueFefo(
                        eq(raw),
                        eq(new BigDecimal("4")),
                        eq(RawMaterialStockService.REF_MATERIAL_ISSUE),
                        eq(5L),
                        isNull(),
                        any()))
                .thenReturn(List.of(new IssueSlice(lot, new BigDecimal("4"), new BigDecimal("1000"))));
        when(materialIssueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MaterialIssueResponse response = service.confirm(5L, new MaterialIssueConfirmRequest(null), null);

        assertThat(issue.getStatus()).isEqualTo(MaterialIssueStatus.COMPLETED);
        assertThat(issue.getCompletedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
        assertThat(order.getActualMaterialCost()).isEqualByComparingTo("4000.00");
        assertThat(response.status()).isEqualTo(MaterialIssueStatus.COMPLETED);
        verify(rawMaterialStockService).issueFefo(eq(raw), eq(new BigDecimal("4")), any(), eq(5L), isNull(), any());
    }

    @Test
    void confirm_whenAlreadyCompleted_isIdempotent() {
        ProductionOrder order = order(1L, ProductionOrderStatus.IN_PROGRESS);
        MaterialIssue issue = pendingIssue(5L, order);
        issue.setStatus(MaterialIssueStatus.COMPLETED);
        when(materialIssueRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(issue));
        when(materialIssueRepository.findWithHeaderById(5L)).thenReturn(Optional.of(issue));
        when(materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(5L)).thenReturn(List.of());

        MaterialIssueResponse response = service.confirm(5L, null, null);

        assertThat(response.status()).isEqualTo(MaterialIssueStatus.COMPLETED);
        verify(rawMaterialStockService, never()).issueFefo(any(), any(), any(), anyLong(), any(), any());
        verify(productionOrderRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void confirm_whenOrderNotApproved_returns409() {
        ProductionOrder order = order(1L, ProductionOrderStatus.DRAFT);
        MaterialIssue issue = pendingIssue(5L, order);
        when(materialIssueRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(issue));
        when(productionOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.confirm(5L, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static ProductionOrder order(Long id, ProductionOrderStatus status) {
        ProductionOrder order = new ProductionOrder();
        ReflectionTestUtils.setField(order, "id", id);
        order.setCode("LSX-" + id);
        order.setStatus(status);
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 10L);
        product.setCode("TP");
        product.setName("TP");
        order.setProduct(product);
        order.setPlannedQty(BigDecimal.TEN);
        return order;
    }

    private static MaterialIssue pendingIssue(Long id, ProductionOrder order) {
        MaterialIssue issue = new MaterialIssue();
        ReflectionTestUtils.setField(issue, "id", id);
        issue.setCode("PX-" + id);
        issue.setProductionOrder(order);
        issue.setStatus(MaterialIssueStatus.PENDING);
        issue.replaceLines(new ArrayList<>());
        order.setMaterialIssue(issue);
        return issue;
    }

    private static RawMaterial raw(Long id) {
        RawMaterial raw = new RawMaterial();
        ReflectionTestUtils.setField(raw, "id", id);
        raw.setCode("NL-" + id);
        raw.setName("NVL");
        raw.setUnit("kg");
        return raw;
    }

    private static MaterialIssueLine requiredLine(RawMaterial raw, String qty) {
        MaterialIssueLine line = new MaterialIssueLine();
        ReflectionTestUtils.setField(line, "id", 1L);
        line.setRawMaterial(raw);
        line.setQtyRequired(new BigDecimal(qty));
        return line;
    }

    private static RawMaterialLot lot(RawMaterial raw, Long id, String code) {
        RawMaterialLot lot = new RawMaterialLot();
        ReflectionTestUtils.setField(lot, "id", id);
        lot.setRawMaterial(raw);
        lot.setLotCode(code);
        lot.setQtyRemaining(new BigDecimal("10"));
        lot.setUnitPrice(new BigDecimal("1000"));
        return lot;
    }
}
