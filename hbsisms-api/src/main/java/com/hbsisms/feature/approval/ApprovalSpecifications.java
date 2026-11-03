package com.hbsisms.feature.approval;

import com.hbsisms.common.text.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class ApprovalSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ApprovalSpecifications() {
    }

    static Specification<ApprovalTask> search(String keyword, ApprovalStatus status, ApprovalType approvalType) {
        List<Specification<ApprovalTask>> specs = new ArrayList<>();
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            String pattern = "%" + Strings.escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
            specs.add((root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE));
        }
        if (status != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (approvalType != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("approvalType"), approvalType));
        }
        return specs.isEmpty() ? (root, query, cb) -> cb.conjunction() : Specification.allOf(specs);
    }
}
