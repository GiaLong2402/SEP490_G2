package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.OwnerInboxItemResponse;
import com.hbsisms.feature.sales.dto.OwnerInboxResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class OrderOwnerInboxServiceTest {

    @Mock
    private ApprovalTaskRepository approvalTaskRepository;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private SalesReturnRepository salesReturnRepository;

    @InjectMocks
    private OrderOwnerInboxService service;

    @Test
    void list_includesPendingOrderApproval() {
        ApprovalTask task = new ApprovalTask();
        task.setRefType(SalesOrderService.REF_TYPE);
        task.setRefId(42L);
        task.setTitle("Đơn vượt hạn mức");
        task.setStatus(ApprovalStatus.PENDING);
        task.setCreatedAt(Instant.parse("2026-11-18T01:00:00Z"));

        SalesOrder order = new SalesOrder();
        // id via reflection not needed — findById returns order with code
        order.setCode("SO-2026-0042");

        when(approvalTaskRepository.findByStatusAndRefTypeOrderByCreatedAtDesc(
                        ApprovalStatus.PENDING, SalesOrderService.REF_TYPE))
                .thenReturn(List.of(task));
        when(salesOrderRepository.findById(42L)).thenReturn(Optional.of(order));
        when(paymentRepository.findIdsWithNeedsRefundFlag()).thenReturn(List.of());
        when(salesOrderRepository.findByPaymentStatusOrderByUpdatedAtDesc(PaymentStatus.REFUND_PENDING))
                .thenReturn(List.of());
        when(salesOrderRepository.findByPaymentStatusOrderByUpdatedAtDesc(PaymentStatus.DIFF))
                .thenReturn(List.of());
        when(paymentRepository.findByReconcileFlagIn(any())).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(salesReturnRepository.findByStatusOrderByCreatedAtDesc(ReturnStatus.PENDING)).thenReturn(List.of());

        OwnerInboxResponse response = service.list();

        assertThat(response.items()).hasSize(1);
        OwnerInboxItemResponse item = response.items().get(0);
        assertThat(item.type()).isEqualTo(OrderOwnerInboxService.TYPE_DISCOUNT_OR_LIMIT);
        assertThat(item.orderId()).isEqualTo(42L);
        assertThat(item.orderCode()).isEqualTo("SO-2026-0042");
        assertThat(item.link()).isEqualTo("/orders/42");
    }

    @Test
    void list_survivesEmptyReturns() {
        when(approvalTaskRepository.findByStatusAndRefTypeOrderByCreatedAtDesc(eq(ApprovalStatus.PENDING), any()))
                .thenReturn(List.of());
        when(paymentRepository.findIdsWithNeedsRefundFlag()).thenReturn(List.of());
        when(salesOrderRepository.findByPaymentStatusOrderByUpdatedAtDesc(any())).thenReturn(List.of());
        when(paymentRepository.findByReconcileFlagIn(any())).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        when(salesReturnRepository.findByStatusOrderByCreatedAtDesc(ReturnStatus.PENDING)).thenReturn(List.of());

        assertThat(service.list().items()).isEmpty();
    }
}
