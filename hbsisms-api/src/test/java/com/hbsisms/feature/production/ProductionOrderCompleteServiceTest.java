package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.hbsisms.feature.inventory.ReceiptStatus;
import com.hbsisms.feature.inventory.ReceiptType;
import com.hbsisms.feature.production.dto.ProductionOrderCompleteRequest;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** Cường — Inter 1 task 51: báo hoàn thành + PN TP Pending. */
@ExtendWith(MockitoExtension.class)
class ProductionOrderCompleteServiceTest {

    @Mock
    private ProductionOrderRepository productionOrderRepository;
    @Mock
    private ProductionOrderMaterialLineRepository materialLineRepository;
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
    private BusinessCodeService businessCodeService;
    @Mock
    private UserRepository userRepository;

    private ProductionOrderService service;

    @BeforeEach
    void setUp() {
        service = new ProductionOrderService(
                productionOrderRepository,
                materialLineRepository,
                productRepository,
                productVariantRepository,
                bomExplosionService,
                materialIssueService,
                goodsReceiptService,
                businessCodeService,
                userRepository);
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

        ProductionOrderResponse response = service.complete(
                7L, new ProductionOrderCompleteRequest(new BigDecimal("12"), new BigDecimal("90000"), null), 1L);

        assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.AWAITING_RECEIPT);
        assertThat(order.getActualQty()).isEqualByComparingTo("12");
        assertThat(order.getActualProductionCost()).isEqualByComparingTo("90000");
        assertThat(order.getFinishedReceipt()).isSameAs(receipt);
        assertThat(response.finishedReceiptCode()).isEqualTo("PN-2026-0088");
        assertThat(response.status()).isEqualTo(ProductionOrderStatus.AWAITING_RECEIPT);
    }

    @Test
    void complete_whenNotInProgress_returns409() {
        ProductionOrder order = inProgressOrder(7L);
        order.setStatus(ProductionOrderStatus.APPROVED);
        when(productionOrderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.complete(
                        7L, new ProductionOrderCompleteRequest(BigDecimal.ONE, null, null), null))
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

        ProductionOrderResponse response = service.complete(
                7L, new ProductionOrderCompleteRequest(new BigDecimal("99"), null, null), null);

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
}
