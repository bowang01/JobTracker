package com.jobtrack.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobtrack.domain.JobApplication;

/**
 * Job applications. Simple queries are derived or declared with @Query and implemented by Spring Data;
 * the dynamic list search lives in {@link JobApplicationRepositoryCustom} (hand-written implementation).
 */
public interface JobApplicationRepository
        extends JpaRepository<JobApplication, UUID>, JobApplicationRepositoryCustom {

    @Query("select new com.jobtrack.repository.StatusCount(a.status, count(a)) "
            + "from JobApplication a where a.owner.id = :ownerId group by a.status")
    List<StatusCount> countByStatusForOwner(@Param("ownerId") UUID ownerId);

    /** Site-wide status distribution, for the admin overview. */
    @Query("select new com.jobtrack.repository.StatusCount(a.status, count(a)) "
            + "from JobApplication a group by a.status")
    List<StatusCount> countByStatusAll();

    List<JobApplication> findTop5ByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    List<JobApplication> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    /** Application count per owner, for user lists (avoids one query per user). */
    @Query("select new com.jobtrack.repository.OwnerCount(a.owner.id, count(a)) "
            + "from JobApplication a group by a.owner.id")
    List<OwnerCount> countPerOwner();

    /** Owners with the most applications. */
    @Query("select new com.jobtrack.repository.OwnerCount(a.owner.id, count(a)) "
            + "from JobApplication a group by a.owner.id order by count(a) desc")
    List<OwnerCount> topOwners(Pageable pageable);

    @Modifying
    @Query("delete from JobApplication a where a.owner.id = :ownerId")
    int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
