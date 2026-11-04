package com.hbsisms.feature.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolumeTierRepository extends JpaRepository<VolumeTier, Long> {

    List<VolumeTier> findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(
            Long variantId, int minQtyKhay);

    List<VolumeTier> findByVariant_IdOrderByMinQtyKhayAsc(Long variantId);
}
