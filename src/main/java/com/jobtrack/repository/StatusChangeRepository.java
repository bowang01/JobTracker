package com.jobtrack.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobtrack.domain.StatusChange;

public interface StatusChangeRepository extends JpaRepository<StatusChange, UUID> {

    @Query("select s from StatusChange s left join fetch s.changedBy "
            + "where s.application.id = :appId order by s.occurredAt desc, s.recordedAt desc")
    List<StatusChange> findTimeline(@Param("appId") UUID appId);

    /** Latest record by occurrence time: its toStatus is the application's current status. */
    Optional<StatusChange> findFirstByApplicationIdOrderByOccurredAtDescRecordedAtDesc(UUID applicationId);

    /** Latest record at or before the given time; used to work out the "from" status when backfilling. */
    Optional<StatusChange> findFirstByApplicationIdAndOccurredAtLessThanEqualOrderByOccurredAtDescRecordedAtDesc(
            UUID applicationId, LocalDateTime occurredAt);

    @Modifying
    @Query("delete from StatusChange s where s.application.id = :appId")
    int deleteByApplicationId(@Param("appId") UUID appId);

    @Modifying
    @Query("delete from StatusChange s where s.application.id in "
            + "(select a.id from JobApplication a where a.owner.id = :ownerId)")
    int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
