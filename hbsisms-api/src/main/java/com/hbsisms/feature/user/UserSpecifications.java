package com.hbsisms.feature.user;

import com.hbsisms.common.text.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

final class UserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private UserSpecifications() {
    }

    static Specification<AppUser> staffSearch(String keyword, Short roleId) {
        List<Specification<AppUser>> specs = new ArrayList<>();
        specs.add(hasType(UserType.STAFF));
        String q = Strings.trimToNull(keyword);
        if (q != null) {
            specs.add(matchesKeyword(q));
        }
        if (roleId != null) {
            specs.add(hasRole(roleId));
        }
        return Specification.allOf(specs);
    }

    private static Specification<AppUser> hasType(UserType type) {
        return (root, query, cb) -> cb.equal(root.get("userType"), type);
    }

    private static Specification<AppUser> hasRole(Short roleId) {
        return (root, query, cb) -> cb.equal(root.get("role").get("id"), roleId);
    }

    /** Case-insensitive contains on full name, username, email and employee code. */
    private static Specification<AppUser> matchesKeyword(String keyword) {
        String pattern = "%" + Strings.escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("fullName")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("username")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("email")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("employeeCode")), pattern, LIKE_ESCAPE));
    }
}
