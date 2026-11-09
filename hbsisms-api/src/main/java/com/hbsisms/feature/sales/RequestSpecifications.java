package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class RequestSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private RequestSpecifications() {}

    static Specification<SalesRequest> search(
            String keyword,
            RequestStatus status,
            SaleChannel channel,
            LocalDate neededFrom,
            LocalDate neededTo,
            Long customerId) {
        List<Specification<SalesRequest>> specs = new ArrayList<>();
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(keywordSpec(q));
        }
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (channel != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("channel"), channel));
        }
        if (customerId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("customer").get("id"), customerId));
        }
        // "Ngày cần" — lọc theo expectedDate nếu có, không thì theo ngày tiếp nhận.
        if (neededFrom != null) {
            Instant from = neededFrom.atStartOfDay().toInstant(ZoneOffset.UTC);
            specs.add((root, query, cb) -> cb.or(
                    cb.and(cb.isNotNull(root.get("expectedDate")), cb.greaterThanOrEqualTo(root.get("expectedDate"), neededFrom)),
                    cb.and(cb.isNull(root.get("expectedDate")), cb.greaterThanOrEqualTo(root.get("receivedAt"), from))));
        }
        if (neededTo != null) {
            Instant to = neededTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            specs.add((root, query, cb) -> cb.or(
                    cb.and(cb.isNotNull(root.get("expectedDate")), cb.lessThanOrEqualTo(root.get("expectedDate"), neededTo)),
                    cb.and(cb.isNull(root.get("expectedDate")), cb.lessThan(root.get("receivedAt"), to))));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
    }

    private static Specification<SalesRequest> keywordSpec(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("customerName")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("summary")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE));
        };
    }
}
