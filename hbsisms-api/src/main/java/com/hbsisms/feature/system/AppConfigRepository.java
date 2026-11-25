package com.hbsisms.feature.system;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigRepository extends JpaRepository<AppConfig, String> {

    @EntityGraph(attributePaths = "updatedBy")
    List<AppConfig> findAllByOrderByKeyAsc();
}
