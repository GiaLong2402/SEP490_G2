package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.payment.PaymentStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {

    private static final char LIKE_ESCAPE = '\\';
    /** Ngày đặt trên UI theo giờ Việt Nam — không dùng UTC (lệch ~7h). */
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final Set<PaymentStatus> AWAITING_PAYMENT_STATUSES =
            EnumSet.of(PaymentStatus.UNPAID, PaymentStatus.WAITING);
    private static final Set<PaymentStatus> PREPARE_PAYMENT_STATUSES =
            EnumSet.of(PaymentStatus.PAID, PaymentStatus.DEBT);

    private OrderSpecifications() {
    }

    static Specification<SalesOrder> search(
            String keyword,
            OrderState state,
            OrderType orderType,
            PaymentStatus paymentStatus,
            SaleChannel channel,
            AllocationStatus allocationStatus,
            Long customerId,
            Long placedBy,
            FulfillmentMethod fulfillmentMethod,
            LocalDate placedFrom,
            LocalDate placedTo,
            LocalDate deliveryFrom,
            LocalDate deliveryTo,
            String timeWindow,
            OrderListGroup group) {
        List<Specification<SalesOrder>> specs = new ArrayList<>();
        // Fetch join cho list (thay EntityGraph trên findAll Spec — tránh Spec bị bỏ qua).
        specs.add(withListFetches());
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(keywordSpec(q));
        }
        if (state != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("state"), state));
        }
        if (orderType != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("orderType"), orderType));
        }
        if (paymentStatus != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("paymentStatus"), paymentStatus));
        }
        if (channel != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("channel"), channel));
        }
        if (allocationStatus != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("allocationStatus"), allocationStatus));
        }
        if (customerId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        if (placedBy != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("placedBy").get("id"), placedBy));
        }
        if (fulfillmentMethod != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("fulfillmentMethod"), fulfillmentMethod));
        }
        if (placedFrom != null) {
            Instant from = placedFrom.atStartOfDay(VN).toInstant();
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("placedAt"), from));
        }
        if (placedTo != null) {
            Instant to = placedTo.plusDays(1).atStartOfDay(VN).toInstant();
            specs.add((root, query, cb) -> cb.lessThan(root.get("placedAt"), to));
        }
        String window = Strings.trimToNull(timeWindow);
        if (deliveryFrom != null || deliveryTo != null || window != null) {
            specs.add(hasDeliveryMatching(deliveryFrom, deliveryTo, window));
        }
        if (group != null) {
            specs.add(groupSpec(group));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
    }

    /** Filter dùng chung cho /orders/counts (không gồm state/group). */
    static Specification<SalesOrder> sharedFilters(
            String keyword,
            OrderType orderType,
            PaymentStatus paymentStatus,
            SaleChannel channel,
            AllocationStatus allocationStatus,
            Long customerId,
            Long placedBy,
            FulfillmentMethod fulfillmentMethod,
            LocalDate placedFrom,
            LocalDate placedTo,
            LocalDate deliveryFrom,
            LocalDate deliveryTo,
            String timeWindow) {
        return search(
                keyword,
                null,
                orderType,
                paymentStatus,
                channel,
                allocationStatus,
                customerId,
                placedBy,
                fulfillmentMethod,
                placedFrom,
                placedTo,
                deliveryFrom,
                deliveryTo,
                timeWindow,
                null);
    }

    static Specification<SalesOrder> groupSpec(OrderListGroup group) {
        return switch (group) {
            case AWAITING_PAYMENT -> (root, query, cb) -> cb.and(
                    root.get("paymentStatus").in(AWAITING_PAYMENT_STATUSES),
                    cb.notEqual(root.get("state"), OrderState.CANCELLED));
            case AWAITING_PREPARE -> (root, query, cb) -> cb.and(
                    cb.equal(root.get("state"), OrderState.CONFIRMED),
                    root.get("paymentStatus").in(PREPARE_PAYMENT_STATUSES),
                    cb.equal(root.get("allocationStatus"), AllocationStatus.FULL));
            case PREPARING -> (root, query, cb) -> cb.equal(root.get("state"), OrderState.PREPARING);
            case READY -> (root, query, cb) -> cb.equal(root.get("state"), OrderState.TO_SHIP);
            case DELIVERING -> hasDeliveryState(DeliveryState.GOING);
            case DELIVERY_FAILED -> deliveryFailedOpen();
            case NEEDS_REFUND -> (root, query, cb) ->
                    cb.equal(root.get("paymentStatus"), PaymentStatus.REFUND_PENDING);
            case COMPLETED -> (root, query, cb) -> cb.equal(root.get("state"), OrderState.COMPLETED);
            case CANCELLED -> (root, query, cb) -> cb.equal(root.get("state"), OrderState.CANCELLED);
        };
    }

    /**
     * Có delivery failed, đơn chưa hoàn tất/hủy, và không còn delivery ready/going
     * (chưa tạo đợt giao lại / chưa xử lý tiếp).
     */
    private static Specification<SalesOrder> deliveryFailedOpen() {
        return Specification.allOf(
                (root, query, cb) -> cb.and(
                        cb.notEqual(root.get("state"), OrderState.COMPLETED),
                        cb.notEqual(root.get("state"), OrderState.CANCELLED)),
                hasDeliveryState(DeliveryState.FAILED),
                Specification.not(hasDeliveryState(DeliveryState.READY)),
                Specification.not(hasDeliveryState(DeliveryState.GOING)));
    }

    private static Specification<SalesOrder> hasDeliveryMatching(
            LocalDate deliveryFrom, LocalDate deliveryTo, String timeWindow) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<Delivery> delivery = sq.from(Delivery.class);
            sq.select(delivery.get("id"));
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(delivery.get("order").get("id"), root.get("id")));
            if (deliveryFrom != null) {
                preds.add(cb.greaterThanOrEqualTo(delivery.get("scheduledDate"), deliveryFrom));
            }
            if (deliveryTo != null) {
                preds.add(cb.lessThanOrEqualTo(delivery.get("scheduledDate"), deliveryTo));
            }
            if (timeWindow != null) {
                preds.add(cb.equal(delivery.get("timeWindow"), timeWindow));
            }
            sq.where(preds.toArray(Predicate[]::new));
            if (query != null
                    && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                query.distinct(true);
            }
            return cb.exists(sq);
        };
    }

    private static Specification<SalesOrder> hasDeliveryState(DeliveryState state) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<Delivery> delivery = sq.from(Delivery.class);
            sq.select(delivery.get("id"));
            sq.where(
                    cb.equal(delivery.get("order").get("id"), root.get("id")),
                    cb.equal(delivery.get("state"), state));
            if (query != null
                    && query.getResultType() != Long.class
                    && query.getResultType() != long.class) {
                query.distinct(true);
            }
            return cb.exists(sq);
        };
    }

    private static Specification<SalesOrder> withListFetches() {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("placedBy", JoinType.LEFT);
                query.distinct(true);
            }
            return cb.conjunction();
        };
    }

    /** Tìm theo mã đơn, tên khách (snapshot/hiện tại), mã KH, SĐT, MST. */
    private static Specification<SalesOrder> keywordSpec(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("customerName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("phone")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("taxCode")), pattern, LIKE_ESCAPE));
        };
    }
}
