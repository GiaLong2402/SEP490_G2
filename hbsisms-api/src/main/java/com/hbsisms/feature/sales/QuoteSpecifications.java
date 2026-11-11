package com.hbsisms.feature.sales;

import com.hbsisms.common.text.Strings;
import jakarta.persistence.criteria.JoinType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class QuoteSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private QuoteSpecifications() {}

    static Specification<Quote> search(String keyword, QuoteStatus status) {
        List<Specification<Quote>> specs = new ArrayList<>();
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(keywordSpec(q));
        }
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return specs.stream().reduce(Specification::and).orElse((root, query, cb) -> cb.conjunction());
    }

    private static Specification<Quote> keywordSpec(String keyword) {
        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            var customer = root.join("customer", JoinType.LEFT);
            return cb.or(
                    cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("name")), pattern, LIKE_ESCAPE),
                    cb.like(cb.lower(customer.get("code")), pattern, LIKE_ESCAPE));
        };
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
