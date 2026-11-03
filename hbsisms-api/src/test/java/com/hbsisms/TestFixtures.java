package com.hbsisms;

import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserType;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/** Builders for entities whose ids are database-generated. */
public final class TestFixtures {

    private TestFixtures() {
    }

    public static AppRole role(short id, String code, Set<Permission> permissions) {
        AppRole role = new AppRole(code, code, permissions);
        ReflectionTestUtils.setField(role, "id", id);
        return role;
    }

    public static AppUser staff(long id, String username, AppRole role) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setFullName("User " + username);
        user.setUserType(UserType.STAFF);
        user.setRole(role);
        user.setPasswordHash("hash-" + username);
        return user;
    }
}
