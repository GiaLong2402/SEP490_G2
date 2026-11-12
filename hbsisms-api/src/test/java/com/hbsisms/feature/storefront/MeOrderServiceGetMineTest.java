package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.PayosClient;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class MeOrderServiceGetMineTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private PayosClient payosClient;

    private MeOrderService service;

    @BeforeEach
    void setUp() {
        service = new MeOrderService(
                salesOrderRepository,
                deliveryRepository,
                paymentRepository,
                orderEventRepository,
                orderAllocationService,
                payosClient);
    }

    @Test
    void getMine_foreignOrderCode_returns404() {
        Customer me = customer(3L);
        when(salesOrderRepository.findByCodeAndCustomer_Id("SO-OTHER", 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(me, "SO-OTHER"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getMessage()).isEqualTo("Không tìm thấy đơn hàng");
                });
    }

    @Test
    void getMine_ownedOrder_returnsDetail() {
        Customer me = customer(3L);
        me.setName("Nguyễn Văn An");
        me.setPhone("0912345678");
        SalesOrder order = ownedOrder(9L, "SO-MINE", me);

        when(salesOrderRepository.findByCodeAndCustomer_Id("SO-MINE", 3L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(9L)).thenReturn(List.of());
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(9L)).thenReturn(List.of());

        MeOrderDetailResponse detail = service.getMine(me, "SO-MINE");

        assertThat(detail.code()).isEqualTo("SO-MINE");
        assertThat(detail.state()).isEqualTo("PENDING");
        assertThat(detail.customerName()).isEqualTo("Nguyễn Văn An");
        verify(salesOrderRepository).findByCodeAndCustomer_Id("SO-MINE", 3L);
    }

    private static Customer customer(long id) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        return customer;
    }

    private static SalesOrder ownedOrder(long id, String code, Customer customer) {
        SalesOrder order = new SalesOrder();
        ReflectionTestUtils.setField(order, "id", id);
        order.setCode(code);
        order.setCustomer(customer);
        order.setState(OrderState.PENDING);
        order.setSubtotal(new BigDecimal("100000"));
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("100000"));
        ReflectionTestUtils.setField(order, "lines", new ArrayList<>());
        return order;
    }
}
