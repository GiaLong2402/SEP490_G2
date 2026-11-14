package com.hbsisms.feature.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findWithRoleById(Long id);

    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByPhone(String phone);

    @EntityGraph(attributePaths = "role")
    Optional<AppUser> findWithRoleByPhone(String phone);

    Optional<AppUser> findByEmployeeCode(String employeeCode);

    /** Tài khoản đang hoạt động theo mã vai trò (vd. {@code owner}) — dùng để gửi thông báo. */
    List<AppUser> findByRole_CodeAndActiveTrue(String roleCode);

    @Override
    @EntityGraph(attributePaths = "role")
    Page<AppUser> findAll(Specification<AppUser> spec, Pageable pageable);

    /** Atomic increment: revokes every JWT issued to the user so far. */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update AppUser u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    int incrementTokenVersion(@Param("id") Long id);

    @Transactional
    @Modifying
    @Query("update AppUser u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLoginAt(@Param("id") Long id, @Param("at") Instant at);

    @Transactional
    @Modifying
    @Query("update AppUser u set u.phoneVerifiedAt = :at where u.id = :id")
    int updatePhoneVerifiedAt(@Param("id") Long id, @Param("at") Instant at);
}
