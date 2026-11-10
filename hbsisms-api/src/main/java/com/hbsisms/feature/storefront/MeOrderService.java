package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderLine;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse.MeCustomer;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse.MeDelivery;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse.MeEvent;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse.MeLine;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse.MePayment;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 #29 list/detail + #30 cancel unpaid — B2C {@code /me/orders}. */
@Service
public class MeOrderService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "placedAt", "id");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryRepository deliveryRepository;
    private final PaymentRepository paymentRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;

    public MeOrderService(
            SalesOrderRepository salesOrderRepository,
            DeliveryRepository deliveryRepository,
            PaymentRepository paymentRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService) {
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryRepository = deliveryRepository;
        this.paymentRepository = paymentRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MeOrderDetailResponse> listMine(Customer customer, int page, int pageSize) {
        var result = salesOrderRepository.findByCustomer_IdOrderByPlacedAtDescIdDesc(
                customer.getId(), PageRequests.of(page, pageSize, LIST_SORT));
        return PageResponse.from(result.map(o -> toMeDetail(requireDetailed(o.getId()))));
    }

    @Transactional(readOnly = true)
    public MeOrderDetailResponse getMine(Customer customer, String code) {
        return toMeDetail(requireOwned(customer, code));
    }

    @Transactional
    public MeOrderDetailResponse cancelMine(Customer customer, String code) {
        SalesOrder order = requireOwned(customer, code);
        if (!portalCanCancel(order)) {
            throw ApiException.conflict("Đơn không thể hủy (chỉ hủy khi chờ thanh toán)");
        }

        Long actorId = customer.getAccountUser() == null ? null : customer.getAccountUser().getId();
        orderAllocationService.releaseForOrder(order, actorId, "Khách hủy đơn — giải phóng hold");
        order.setState(OrderState.CANCELLED);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setHoldExpiresAt(null);
        salesOrderRepository.save(order);

        for (Payment payment : paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId())) {
            if (payment.getMethod() == PaymentMethod.PAYOS && payment.getStatus() == PayTxnStatus.PENDING) {
                payment.setStatus(PayTxnStatus.CANCELLED);
                payment.setCheckoutUrl(null);
                payment.setNote("Stub: khách hủy đơn — Long thay cancel PayOS thật (#33)");
                paymentRepository.save(payment);
            }
        }

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("cancelled");
        event.setDescription("Khách hủy đơn chưa thanh toán");
        event.setCreatedBy(customer.getAccountUser());
        orderEventRepository.save(event);

        return toMeDetail(requireDetailed(order.getId()));
    }

    private SalesOrder requireOwned(Customer customer, String code) {
        String key = Strings.trimToNull(code);
        if (key == null) {
            throw ApiException.badRequest("Thiếu mã đơn");
        }
        SalesOrder header = salesOrderRepository
                .findByCodeAndCustomer_Id(key, customer.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        return requireDetailed(header.getId());
    }

    private SalesOrder requireDetailed(Long id) {
        return salesOrderRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
    }

    static boolean portalCanCancel(SalesOrder order) {
        return order.getState() == OrderState.PENDING && order.getPaymentStatus() != PaymentStatus.PAID;
    }

    private MeOrderDetailResponse toMeDetail(SalesOrder order) {
        List<Delivery> deliveries = deliveryRepository.findByOrder_IdOrderByIdAsc(order.getId());

        List<MeLine> lines = order.getLines().stream().map(this::toLine).toList();

        List<Payment> allPayments = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId());
        List<Payment> visible = allPayments.stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS || p.getStatus() == PayTxnStatus.PENDING)
                .toList();
        BigDecimal paidAmount = allPayments.stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS)
                .map(p -> p.getDirection() == PayDirection.IN ? p.getAmount() : p.getAmount().negate())
                .reduce(ZERO, BigDecimal::add);

        List<MePayment> payments = visible.stream()
                .map(p -> new MePayment(
                        enumName(p.getMethod()),
                        enumName(p.getDirection()),
                        enumName(p.getStatus()),
                        p.getPaidAt(),
                        p.getCheckoutUrl(),
                        p.getPayosOrderCode()))
                .toList();

        List<MeEvent> events = orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt)
                        .reversed()
                        .thenComparing(OrderEvent::getId, Comparator.reverseOrder()))
                .map(e -> new MeEvent(e.getId(), e.getEventType(), e.getDescription(), e.getCreatedAt()))
                .toList();

        Customer customer = order.getCustomer();
        MeCustomer meCustomer = customer == null
                ? null
                : new MeCustomer(customer.getName(), customer.getPhone());

        List<MeDelivery> meDeliveries = deliveries.stream()
                .map(d -> new MeDelivery(d.getScheduledDate(), d.getTimeWindow()))
                .toList();

        return new MeOrderDetailResponse(
                order.getId(),
                order.getCode(),
                enumName(order.getState()),
                enumName(order.getPaymentStatus()),
                enumName(order.getFulfillmentMethod()),
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getShippingFee(),
                order.getTotal(),
                paidAmount,
                order.getPlacedAt(),
                order.getHoldExpiresAt(),
                order.getNote() == null ? "" : order.getNote(),
                portalCanCancel(order),
                customer == null ? null : customer.getName(),
                meCustomer,
                order.getShipAddress() == null ? null : AddressResponse.from(order.getShipAddress()),
                lines,
                meDeliveries,
                payments,
                events);
    }

    private MeLine toLine(SalesOrderLine line) {
        var variant = line.getVariant();
        var product = variant.getProduct();
        return new MeLine(
                line.getId(),
                variant.getId(),
                product.getName(),
                variant.getPackLabel(),
                variant.getSku(),
                line.getQtyKhay(),
                line.getUnitPrice(),
                line.getLineTotal(),
                product.getImageUrl());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
