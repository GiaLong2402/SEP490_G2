package com.hbsisms.feature.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductBomLineRepository extends JpaRepository<ProductBomLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "componentProduct", "unit"})
    List<ProductBomLine> findByProduct_IdOrderBySortOrderAscIdAsc(Long productId);

    void deleteByProduct_Id(Long productId);

    boolean existsByUnit_Id(Long unitId);

    /** Thành phẩm đang dùng + đã có ít nhất 1 dòng BOM nguyên liệu (Inter1 #48, quyền inventory). */
    @Query("""
            select p from Product p
            where p.active = true and p.rawMaterial = false
              and exists (
                select 1 from ProductBomLine b
                where b.product = p and b.rawMaterial is not null
              )
            order by p.producible desc, p.name asc
            """)
    List<Product> findActiveProductsWithRawBom();

    @Query("""
            select count(b) from ProductBomLine b
            where b.product.id = :productId and b.rawMaterial is not null
            """)
    long countRawLinesByProductId(@Param("productId") Long productId);
}
