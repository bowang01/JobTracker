package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.domain.JobApplication;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.security.AppUserDetails;

import com.jobtrack.service.AccessGuard;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.DocumentService;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.NotFoundException;
import com.jobtrack.service.StoredFile;

/**
 * Rules 5/6: documents are versioned per type. Every upload creates a new version and a new file on disk;
 * older versions are kept until the user explicitly deletes one.
 */
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final AccessGuard guard;
    private final Clock clock;

    public DocumentServiceImpl(DocumentRepository documents, FileStorageService storage, AccessGuard guard, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional
    @Override
    public ApplicationDocument upload(UUID appId, DocumentType type, MultipartFile file, AppUserDetails me) {
        JobApplication app = guard.loadForWrite(appId, me);
        if (type == null) {
            throw new BusinessException("Please choose a document type");
        }
        StoredFile stored = storage.store(file);
        storage.deleteOnRollback(stored.storedFilename());

        Integer max = documents.findMaxVersion(appId, type);
        int version = max == null ? 1 : max + 1;
        ApplicationDocument doc = new ApplicationDocument(app, type, version,
                stored.originalFilename(), stored.storedFilename(), stored.contentType(),
                stored.sizeBytes(), stored.sha256(), LocalDateTime.now(clock));
        return documents.saveAndFlush(doc);
    }

    /** Permission check before download: document -> application -> owner/admin. */
    @Transactional(readOnly = true)
    @Override
    public ApplicationDocument getForDownload(UUID documentId, AppUserDetails me) {
        ApplicationDocument doc = documents.findById(documentId).orElseThrow(NotFoundException::new);
        guard.loadForRead(doc.getApplication().getId(), me);
        return doc;
    }

    /** Deletes one version and returns the application ID (for the redirect). */
    @Transactional
    @Override
    public UUID delete(UUID documentId, AppUserDetails me) {
        ApplicationDocument doc = documents.findById(documentId).orElseThrow(NotFoundException::new);
        UUID appId = doc.getApplication().getId();
        guard.loadForWrite(appId, me);
        documents.delete(doc);
        storage.deleteAfterCommit(List.of(doc.getStoredFilename()));
        return appId;
    }
}
