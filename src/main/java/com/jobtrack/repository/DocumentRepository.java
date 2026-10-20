package com.jobtrack.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;

public interface DocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

    List<ApplicationDocument> findByApplicationIdOrderByDocTypeAscVersionNoDesc(UUID applicationId);

    List<ApplicationDocument> findByApplicationIdAndDocTypeOrderByVersionNoDesc(UUID applicationId, DocumentType docType);

    @Query("select max(d.versionNo) from ApplicationDocument d "
            + "where d.application.id = :appId and d.docType = :type")
    Integer findMaxVersion(@Param("appId") UUID appId, @Param("type") DocumentType type);

    @Query("select d.storedFilename from ApplicationDocument d where d.application.id = :appId")
    List<String> findStoredFilenamesByApplicationId(@Param("appId") UUID appId);

    @Query("select d.storedFilename from ApplicationDocument d where d.application.owner.id = :ownerId")
    List<String> findStoredFilenamesByOwnerId(@Param("ownerId") UUID ownerId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from ApplicationDocument d")
    long totalBytes();

    @Query("select count(d) from ApplicationDocument d where d.application.owner.id = :ownerId")
    long countForOwner(@Param("ownerId") UUID ownerId);

    @Query("select coalesce(sum(d.sizeBytes), 0) from ApplicationDocument d where d.application.owner.id = :ownerId")
    long totalBytesForOwner(@Param("ownerId") UUID ownerId);

    @Modifying
    @Query("delete from ApplicationDocument d where d.application.id = :appId")
    int deleteByApplicationId(@Param("appId") UUID appId);

    @Modifying
    @Query("delete from ApplicationDocument d where d.application.id in "
            + "(select a.id from JobApplication a where a.owner.id = :ownerId)")
    int deleteByOwnerId(@Param("ownerId") UUID ownerId);
}
