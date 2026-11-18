package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xuất Excel danh sách đơn theo bộ lọc list (Inter 3 dòng 12).
 * Tách class để tránh đụng luồng create/cancel của Duy.
 */
@Service
public class OrderExportService {

    static final int MAX_ROWS = 5000;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "placedAt");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private static final String[] HEADERS = {
        "Mã đơn",
        "Ngày đặt",
        "Khách hàng",
        "Kênh",
        "Trạng thái đơn",
        "Thanh toán",
        "Trạng thái giao",
        "Tổng tiền",
        "Đã thu"
    };

    private final SalesOrderRepository salesOrderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;

    public OrderExportService(
            SalesOrderRepository salesOrderRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(readOnly = true)
    public byte[] export(
            String q,
            String state,
            String orderType,
            String paymentStatus,
            String channel,
            String allocationStatus,
            Long customerId,
            Long placedBy,
            String fulfillmentMethod,
            String placedFrom,
            String placedTo,
            String deliveryFrom,
            String deliveryTo,
            String timeWindow,
            String group) {
        Specification<SalesOrder> spec = OrderSpecifications.search(
                q,
                parseState(state),
                parseOrderType(orderType),
                parsePaymentStatus(paymentStatus),
                parseChannel(channel),
                parseAllocationStatus(allocationStatus),
                customerId,
                placedBy,
                parseFulfillmentMethod(fulfillmentMethod),
                parseDate(placedFrom),
                parseDate(placedTo),
                parseDate(deliveryFrom),
                parseDate(deliveryTo),
                timeWindow,
                parseGroup(group));

        long total = salesOrderRepository.count(spec);
        if (total > MAX_ROWS) {
            throw ApiException.badRequest(
                    "Quá " + MAX_ROWS + " đơn (" + total + "). Thu hẹp bộ lọc rồi xuất lại.");
        }

        List<SalesOrder> orders = salesOrderRepository.findAll(spec, LIST_SORT);
        Map<Long, BigDecimal> paidByOrder = netPaidByOrder(orders);
        Map<Long, String> deliveryByOrder = deliverySummaryByOrder(orders);
        return writeWorkbook(orders, paidByOrder, deliveryByOrder);
    }

    private Map<Long, BigDecimal> netPaidByOrder(List<SalesOrder> orders) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        if (orders.isEmpty()) {
            return paid;
        }
        List<Long> ids = orders.stream().map(SalesOrder::getId).toList();
        for (Payment p : paymentRepository.findByOrder_IdIn(ids)) {
            if (p.getStatus() != PayTxnStatus.SUCCESS || p.getOrder() == null) {
                continue;
            }
            Long orderId = p.getOrder().getId();
            BigDecimal delta = p.getDirection() == PayDirection.IN
                    ? p.getAmount()
                    : p.getAmount().negate();
            paid.merge(orderId, delta, BigDecimal::add);
        }
        return paid;
    }

    private Map<Long, String> deliverySummaryByOrder(List<SalesOrder> orders) {
        Map<Long, String> summary = new HashMap<>();
        if (orders.isEmpty()) {
            return summary;
        }
        List<Long> ids = orders.stream().map(SalesOrder::getId).toList();
        Map<Long, Set<DeliveryState>> statesByOrder = new HashMap<>();
        for (Delivery d : deliveryRepository.findByOrder_IdIn(ids)) {
            if (d.getOrder() == null || d.getState() == null) {
                continue;
            }
            statesByOrder
                    .computeIfAbsent(d.getOrder().getId(), ignored -> new LinkedHashSet<>())
                    .add(d.getState());
        }
        for (Map.Entry<Long, Set<DeliveryState>> e : statesByOrder.entrySet()) {
            summary.put(e.getKey(), summarizeDelivery(e.getValue()));
        }
        return summary;
    }

    /** Ưu tiên: thất bại mở > đang giao > chờ giao > đã giao. */
    static String summarizeDelivery(Set<DeliveryState> states) {
        if (states == null || states.isEmpty()) {
            return "";
        }
        if (states.contains(DeliveryState.FAILED)
                && !states.contains(DeliveryState.READY)
                && !states.contains(DeliveryState.GOING)) {
            return labelDelivery(DeliveryState.FAILED);
        }
        if (states.contains(DeliveryState.GOING)) {
            return labelDelivery(DeliveryState.GOING);
        }
        if (states.contains(DeliveryState.READY)) {
            return labelDelivery(DeliveryState.READY);
        }
        if (states.contains(DeliveryState.DELIVERED)) {
            return labelDelivery(DeliveryState.DELIVERED);
        }
        return states.iterator().next().code();
    }

    private byte[] writeWorkbook(
            List<SalesOrder> orders,
            Map<Long, BigDecimal> paidByOrder,
            Map<Long, String> deliveryByOrder) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Don hang");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (SalesOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(nullToEmpty(order.getCode()));
                row.createCell(1)
                        .setCellValue(
                                order.getPlacedAt() == null
                                        ? ""
                                        : DATE_FMT.format(order.getPlacedAt().atZone(VN)));
                row.createCell(2).setCellValue(customerName(order));
                row.createCell(3).setCellValue(labelChannel(order.getChannel()));
                row.createCell(4).setCellValue(labelOrderState(order.getState()));
                row.createCell(5).setCellValue(labelPaymentStatus(order.getPaymentStatus()));
                row.createCell(6)
                        .setCellValue(deliveryByOrder.getOrDefault(order.getId(), ""));
                row.createCell(7)
                        .setCellValue(order.getTotal() == null ? 0d : order.getTotal().doubleValue());
                BigDecimal paid = paidByOrder.getOrDefault(order.getId(), ZERO);
                row.createCell(8).setCellValue(paid.doubleValue());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không tạo được file Excel", ex);
        }
    }

    private static String customerName(SalesOrder order) {
        if (Strings.trimToNull(order.getCustomerName()) != null) {
            return order.getCustomerName().trim();
        }
        return order.getCustomer() == null ? "Khách vãng lai" : order.getCustomer().getName();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String labelChannel(SaleChannel channel) {
        if (channel == null) {
            return "";
        }
        return switch (channel) {
            case WEB -> "Website";
            case PHONE -> "Điện thoại";
            case ZALO -> "Zalo";
            case FACEBOOK -> "Facebook";
            case COUNTER -> "Quầy";
            case APP -> "App";
        };
    }

    private static String labelOrderState(OrderState state) {
        if (state == null) {
            return "";
        }
        return switch (state) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case PREPARING -> "Đang chuẩn bị";
            case TO_SHIP -> "Chờ giao";
            case PARTIAL -> "Giao một phần";
            case DELIVERED -> "Đã giao";
            case COMPLETED -> "Hoàn tất";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String labelPaymentStatus(PaymentStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case UNPAID -> "Chưa thu";
            case WAITING -> "Chờ PayOS";
            case PAID -> "Đã thu";
            case PARTIAL -> "Thu một phần";
            case DEBT -> "Công nợ";
            case DIFF -> "Lệch tiền";
            case REFUND_PENDING -> "Chờ hoàn tiền";
            case REFUNDED -> "Đã hoàn";
        };
    }

    private static String labelDelivery(DeliveryState state) {
        return switch (state) {
            case READY -> "Chờ giao";
            case GOING -> "Đang giao";
            case DELIVERED -> "Đã giao";
            case FAILED -> "Giao thất bại";
        };
    }

    private static OrderState parseState(String raw) {
        try {
            return OrderState.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static OrderType parseOrderType(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OrderType.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static PaymentStatus parsePaymentStatus(String raw) {
        try {
            return PaymentStatus.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static SaleChannel parseChannel(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return SaleChannel.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static AllocationStatus parseAllocationStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return AllocationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Trạng thái phân bổ không hợp lệ: " + raw);
        }
    }

    private static FulfillmentMethod parseFulfillmentMethod(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return FulfillmentMethod.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static OrderListGroup parseGroup(String raw) {
        try {
            return OrderListGroup.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static LocalDate parseDate(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw ApiException.badRequest("Ngày không hợp lệ: " + raw);
        }
    }
}
