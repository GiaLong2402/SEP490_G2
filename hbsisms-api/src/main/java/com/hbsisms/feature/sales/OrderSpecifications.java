package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.payment.PaymentStatus;
import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private OrderSpecifications() {
    }

    static Specification<SalesOrder> search(
            String keyword, OrderState state, OrderType orderType, PaymentStatus paymentStatus) {
        List<Specification<SalesOrder>> specs = new ArrayList<>();
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
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
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
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE));
        };
    }
}
