package com.hbsisms.feature.customer;

import com.hbsisms.common.text.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filters for customer list/search — matches FE {@code GET /customers?q&type&isActive}. */
final class CustomerSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private CustomerSpecifications() {
    }

    static Specification<Customer> search(String keyword, CustomerType type, Boolean isActive) {
        List<Specification<Customer>> specs = new ArrayList<>();
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(matchesKeyword(q));
        }
        if (type != null) {
            specs.add(hasType(type));
        }
        if (isActive != null) {
            specs.add(hasActive(isActive));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
    }

    private static Specification<Customer> hasType(CustomerType type) {
        return (root, query, cb) -> cb.equal(root.get("customerType"), type);
    }

    private static Specification<Customer> hasActive(boolean isActive) {
        return (root, query, cb) -> cb.equal(root.get("active"), isActive);
    }

    /** Case-insensitive contains on name, code, phone, tax code (MST). */
    private static Specification<Customer> matchesKeyword(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("phone")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("taxCode")), pattern, LIKE_ESCAPE));
    }
}
