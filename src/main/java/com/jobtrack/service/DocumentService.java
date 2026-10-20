package com.jobtrack.service;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.security.AppUserDetails;

/**
 * Rules 5/6: documents are versioned per type. Every upload creates a new version and a new file on disk;
 * older versions are kept until the user explicitly deletes one.
 */
public interface DocumentService {
    ApplicationDocument upload(UUID appId, DocumentType type, MultipartFile file, AppUserDetails me);

    /** Permission check before download: document -> application -> owner/admin. */
    ApplicationDocument getForDownload(UUID documentId, AppUserDetails me);

    /** Deletes one version and returns the application ID (for the redirect). */
    UUID delete(UUID documentId, AppUserDetails me);
}
