package com.hbsisms.feature.catalog;

import com.hbsisms.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A product; sellable SKUs are its {@link ProductVariant}s. {@code published} = visible on the B2C portal. */
@Entity
@DynamicUpdate
@Table(name = "product", indexes = @Index(name = "idx_product_category", columnList = "category_id"))
@Getter
@Setter
@NoArgsConstructor
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(unique = true, length = 220)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String storageNote;

    @Column(length = 160)
    private String origin;

    /** Main image. */
    @Column(length = 500)
    private String imageUrl;

    /**
     * Extra image URLs. text[] like {@code app_role.permissions}: mapping one {@code List<String>} as JSON
     * makes Hibernate bind every other {@code List<String>} attribute as jsonb too.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> gallery = new ArrayList<>();

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public List<String> getGallery() {
        return gallery == null ? List.of() : List.copyOf(gallery);
    }

    public void setGallery(List<String> gallery) {
        this.gallery = gallery == null ? new ArrayList<>() : new ArrayList<>(gallery);
    }
}
