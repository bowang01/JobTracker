package com.jobtrack.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    long countByRole(Role role);

    long countByRoleAndEnabled(Role role, boolean enabled);

    long countByRoleAndEnabledTrue(Role role);

    long countByRoleAndCreatedAtGreaterThanEqual(Role role, LocalDateTime since);

    long countByRoleAndLastLoginAtGreaterThanEqual(Role role, LocalDateTime since);

    List<User> findTop5ByRoleOrderByCreatedAtDesc(Role role);

    /** Admin user list: optional email/display-name keyword and optional enabled filter. */
    @Query("select u from User u where u.role = :role "
            + "and (:q is null or lower(u.email) like :q or lower(u.displayName) like :q) "
            + "and (:enabled is null or u.enabled = :enabled) "
            + "order by u.createdAt desc")
    List<User> search(@Param("role") Role role, @Param("q") String likePattern, @Param("enabled") Boolean enabled);

    List<User> findByRoleOrderByEmailAsc(Role role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.lastLoginAt = :at where u.id = :id")
    int updateLastLogin(@Param("id") UUID id, @Param("at") LocalDateTime at);
}
