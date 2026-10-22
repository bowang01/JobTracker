package com.jobtrack.web.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.jobtrack.domain.ApplicationDocument;
import com.jobtrack.domain.DocumentType;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.DocumentService;
import com.jobtrack.service.FileStorageService;

@Controller
public class DocumentController {

    private final DocumentService service;
    private final FileStorageService storage;

    public DocumentController(DocumentService service, FileStorageService storage) {
        this.service = service;
        this.storage = storage;
    }

    @PostMapping("/applications/{appId}/documents")
    public String upload(@PathVariable UUID appId,
                         @RequestParam(name = "type", required = false) DocumentType type,
                         @RequestParam(name = "file", required = false) MultipartFile file,
                         @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        try {
            ApplicationDocument doc = service.upload(appId, type, file, me);
            ra.addFlashAttribute("success", "Uploaded " + doc.getDocType().getLabel() + " version " + doc.getVersionNo());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("error", "Upload conflict, please try again");
        }
        ra.addFlashAttribute("scrollTo", "documents"); // scroll there without a #fragment in the URL
        return "redirect:/applications/" + appId;
    }

    /**
     * Opens a PDF in the browser (the detail page links here with target="_blank").
     * Only documents stored as PDF are served inline: their type was checked by extension and file signature
     * on upload. Older non-PDF documents are always downloaded instead.
     */
    @GetMapping("/documents/{id}/view")
    public ResponseEntity<Resource> view(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
        ApplicationDocument doc = service.getForDownload(id, me);
        if (!isPdf(doc)) {
            return download(id, me);
        }
        Resource resource = storage.load(doc.getStoredFilename());
        ContentDisposition cd = ContentDisposition.inline()
                .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                // Replaces the site-wide CSP for this response only: the browser's PDF viewer needs object-src,
                // and nothing else (scripts, styles, connections) is allowed. Not frameable.
                .header("Content-Security-Policy", "default-src 'none'; object-src 'self'; frame-ancestors 'none'")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(doc.getSizeBytes())
                .body(resource);
    }

    private static boolean isPdf(ApplicationDocument doc) {
        return MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(doc.getContentType())
                && doc.getStoredFilename().endsWith(".pdf");
    }

    /** Served as an attachment, without trusting the uploaded filename or MIME type. */
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
        ApplicationDocument doc = service.getForDownload(id, me);
        Resource resource = storage.load(doc.getStoredFilename());
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .contentLength(doc.getSizeBytes())
                .body(resource);
    }

    @PostMapping("/documents/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me, RedirectAttributes ra) {
        UUID appId = service.delete(id, me);
        ra.addFlashAttribute("success", "Document version deleted");
        ra.addFlashAttribute("scrollTo", "documents"); // scroll there without a #fragment in the URL
        return "redirect:/applications/" + appId;
    }
}
