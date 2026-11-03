package com.hbsisms.feature.role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One role per user; permissions are a text[] column (see database/README.md). */
@Entity
@Table(name = "app_role")
@Getter
@NoArgsConstructor
public class AppRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Setter
    @Column(nullable = false, length = 80)
    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> permissions = new ArrayList<>();

    public AppRole(String code, String name, Collection<Permission> permissions) {
        this.code = code;
        this.name = name;
        replacePermissions(permissions);
    }

    public List<String> getPermissions() {
        return List.copyOf(permissions);
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission.code());
    }

    public void replacePermissions(Collection<Permission> newPermissions) {
        this.permissions = newPermissions.stream().distinct().sorted().map(Permission::code).toList();
    }
}
