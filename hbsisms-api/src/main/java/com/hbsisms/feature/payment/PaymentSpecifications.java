package com.hbsisms.feature.payment;

import com.hbsisms.common.text.Strings;
import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class PaymentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private PaymentSpecifications() {
    }

    static Specification<Payment> search(String keyword, PaymentMethod method, PayDirection direction) {
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
