package com.hbsisms.feature.system;

import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Key–value settings (default NET days, low-stock threshold, delivery radius, …).
 * Integrations such as PayOS are stored as rows with {@code valueType = json}; secrets belong in env vars, not here.
 */
@Entity
@Table(name = "app_config")
@Getter
@Setter
@NoArgsConstructor
public class AppConfig {

    @Id
    @Column(name = "key", length = 60)
    private String key;

    @Column(columnDefinition = "text")
    private String value;

    /** string | int | bool | json */
    @Column(nullable = false, length = 20)
    private String valueType = "string";

    @Column(length = 255)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
