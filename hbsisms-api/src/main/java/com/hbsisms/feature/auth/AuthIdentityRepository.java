package com.hbsisms.feature.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    @EntityGraph(attributePaths = {"user", "user.role"})
    Optional<AuthIdentity> findByProviderAndProviderUid(String provider, String providerUid);

    boolean existsByUser_IdAndProvider(Long userId, String provider);
}
