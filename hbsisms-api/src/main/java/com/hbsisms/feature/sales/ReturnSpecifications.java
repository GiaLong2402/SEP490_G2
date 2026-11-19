package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Filters for GET /returns. */
final class ReturnSpecifications {

    private ReturnSpecifications() {}

    static Specification<SalesReturn> search(String q, ReturnStatus status) {
        return (root, query, cb) -> {
            if (query != null && SalesReturn.class.equals(query.getResultType())) {
                root.fetch("order", JoinType.LEFT);
                root.fetch("customer", JoinType.LEFT);
                root.fetch("createdBy", JoinType.LEFT);
                query.distinct(true);
            }

            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            String term = Strings.trimToNull(q);
            if (term != null) {
                String pattern = "%" + term.toLowerCase() + "%";
                Join<SalesReturn, SalesOrder> order = root.join("order", JoinType.LEFT);
                Join<SalesReturn, ?> customer = root.join("customer", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(order.get("code")), pattern),
                        cb.like(cb.lower(customer.get("name")), pattern)));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
