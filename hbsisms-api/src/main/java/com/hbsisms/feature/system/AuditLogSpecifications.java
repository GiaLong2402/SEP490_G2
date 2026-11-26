package com.hbsisms.feature.system;

import com.hbsisms.common.text.Strings;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class AuditLogSpecifications {

    /** Ngày trên UI theo giờ Việt Nam — không dùng UTC (lệch ~7h). */
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private AuditLogSpecifications() {}

    static Specification<AuditLog> search(
            String keyword, String action, String entityType, LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            String actionFilter = Strings.trimToNull(action);
            if (actionFilter != null) {
                predicates.add(cb.equal(cb.lower(root.get("action")), actionFilter.toLowerCase(Locale.ROOT)));
            }
            String entityFilter = Strings.trimToNull(entityType);
            if (entityFilter != null) {
                predicates.add(cb.equal(cb.lower(root.get("entityType")), entityFilter.toLowerCase(Locale.ROOT)));
            }
            if (from != null) {
                Instant start = from.atStartOfDay(VN).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), start));
            }
            if (to != null) {
                Instant end = to.plusDays(1).atStartOfDay(VN).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), end));
            }
            String q = Strings.trimToNull(keyword);
            if (q != null) {
                String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
                var user = root.join("user", JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(user.get("fullName"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(user.get("username"), "")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
