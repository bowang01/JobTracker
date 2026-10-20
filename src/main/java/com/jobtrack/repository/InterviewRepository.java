package com.jobtrack.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobtrack.domain.Interview;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    List<Interview> findByApplicationIdOrderByRoundNumberAsc(UUID applicationId);

    boolean existsByApplicationIdAndRoundNumber(UUID applicationId, int roundNumber);

    boolean existsByApplicationIdAndRoundNumberAndIdNot(UUID applicationId, int roundNumber, UUID id);

    @Query("select max(i.roundNumber) from Interview i where i.application.id = :appId")
    Integer findMaxRound(@Param("appId") UUID appId);

    @Query("select i from Interview i join fetch i.application a "
            + "where a.owner.id = :ownerId and i.scheduledAt >= :from and i.scheduledAt < :to "
            + "order by i.scheduledAt asc")
    List<Interview> findForOwnerBetween(@Param("ownerId") UUID ownerId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    long countByScheduledAtGreaterThanEqualAndScheduledAtLessThan(LocalDateTime from, LocalDateTime to);

    @Query("select count(i) from Interview i where i.application.owner.id = :ownerId")
    long countForOwner(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query("delete from Interview i where i.application.id = :appId")
    int deleteByApplicationId(@Param("appId") UUID appId);

    @Modifying
    @Query("delete from Interview i where i.application.id in "
            + "(select a.id from JobApplication a where a.owner.id = :ownerId)")
    int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
