package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.payment.PaymentStatus;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {

    private static final char LIKE_ESCAPE = '\\';
    /** Ngày đặt trên UI theo giờ Việt Nam — không dùng UTC (lệch ~7h). */
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

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
            LocalDate placedFrom,
            LocalDate placedTo) {
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
        if (placedFrom != null) {
            Instant from = placedFrom.atStartOfDay(VN).toInstant();
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("placedAt"), from));
        }
        if (placedTo != null) {
            Instant to = placedTo.plusDays(1).atStartOfDay(VN).toInstant();
            specs.add((root, query, cb) -> cb.lessThan(root.get("placedAt"), to));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
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
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE));
        };
    }
}
