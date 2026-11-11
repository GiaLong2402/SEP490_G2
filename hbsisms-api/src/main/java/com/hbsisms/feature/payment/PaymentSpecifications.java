package com.hbsisms.feature.payment;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.sales.SaleChannel;
import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class PaymentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private PaymentSpecifications() {}

    static Specification<Payment> search(
            String keyword,
            PaymentMethod method,
            PayDirection direction,
            PayTxnStatus status,
            SaleChannel channel,
            Long orderId,
            PaymentReconcileFlag reconcileFlag) {
        List<Specification<Payment>> specs = new ArrayList<>();
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(keywordSpec(q));
        }
        if (method != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("method"), method));
        }
        if (direction != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("direction"), direction));
        }
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (channel != null) {
            specs.add((root, query, cb) -> {
                var order = root.join("order", JoinType.INNER);
                return cb.equal(order.get("channel"), channel);
            });
        }
        if (orderId != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("order").get("id"), orderId));
        }
        if (reconcileFlag != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("reconcileFlag"), reconcileFlag));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
    }

    private static Specification<Payment> keywordSpec(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            var order = root.join("order", JoinType.LEFT);
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(root.get("reference")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(order.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE));
        };
    }
}
