package com.jobtrack.service;

import java.nio.file.Files;
import java.util.Collection;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * On-disk file storage.
 * <ul>
 *   <li>Files live outside the web root and are only reachable through the permission-checked download endpoint;</li>
 *   <li>On-disk names are random UUIDs; the user's filename is only stored in the database for display, which rules out path traversal and overwrites;</li>
 *   <li>Files are deleted after the database transaction commits; files written in a transaction that rolls back are cleaned up (rule 7).</li>
 * </ul>
 */
public interface FileStorageService {
    long getMaxBytes();

    String getMaxSizeLabel();

    /** Validates and stores an upload and returns its metadata. Throws BusinessException when validation fails. */
    StoredFile store(MultipartFile file);

    Resource load(String storedFilename);

    boolean exists(String storedFilename);

    /** Deletes these files after the current transaction commits, or immediately when there is no transaction. */
    void deleteAfterCommit(Collection<String> storedFilenames);

    /** Deletes a just-written file if the current transaction does not commit, so no orphan files are left. */
    void deleteOnRollback(String storedFilename);
}
