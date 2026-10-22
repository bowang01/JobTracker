package com.jobtrack.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.jobtrack.config.JobTrackProperties;

import com.jobtrack.service.AllowedFileType;
import com.jobtrack.service.BusinessException;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.NotFoundException;
import com.jobtrack.service.StoredFile;

/**
 * On-disk file storage.
 * <ul>
 *   <li>Files live outside the web root and are only reachable through the permission-checked download endpoint;</li>
 *   <li>On-disk names are random UUIDs; the user's filename is only stored in the database for display, which rules out path traversal and overwrites;</li>
 *   <li>Files are deleted after the database transaction commits; files written in a transaction that rolls back are cleaned up (rule 7).</li>
 * </ul>
 */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.[a-z0-9]{1,5}$");

    private final Path root;
    private final long maxBytes;

    public FileStorageServiceImpl(JobTrackProperties props) {
        this.root = Paths.get(props.storage().dir()).toAbsolutePath().normalize();
        this.maxBytes = props.upload().maxFileSize().toBytes();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + root, e);
        }
        log.info("File storage directory: {}", root);
    }

    @Override
    public long getMaxBytes() {
        return maxBytes;
    }

    @Override
    public String getMaxSizeLabel() {
        return (maxBytes / (1024 * 1024)) + "MB";
    }

    /** Validates and stores an upload and returns its metadata. Throws BusinessException when validation fails. */
    @Override
    public StoredFile store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Please choose a file to upload (empty files are not allowed)");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("File too large: the limit is " + getMaxSizeLabel() + " per file");
        }
        String original = sanitizeFilename(file.getOriginalFilename());
        String ext = extensionOf(original);
        AllowedFileType type = AllowedFileType.fromExtension(ext)
                .orElseThrow(() -> new BusinessException("Only PDF files can be uploaded"));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the uploaded file", e);
        }
        if (bytes.length == 0) {
            throw new BusinessException("Please choose a file to upload (empty files are not allowed)");
        }
        if (bytes.length > maxBytes) {
            throw new BusinessException("File too large: the limit is " + getMaxSizeLabel() + " per file");
        }
        if (!type.contentMatches(bytes)) {
            throw new BusinessException("This is not a valid PDF file (it may be corrupt or renamed)");
        }

        String storedName = UUID.randomUUID() + "." + type.extension();
        Path target = resolve(storedName);
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save the file", e);
        }
        return new StoredFile(storedName, original, type.contentType(), bytes.length, sha256(bytes));
    }

    @Override
    public Resource load(String storedFilename) {
        Path p = resolve(storedFilename);
        if (!Files.isRegularFile(p) || !Files.isReadable(p)) {
            log.warn("Database record exists but the file is missing on disk: {}", storedFilename);
            throw new NotFoundException();
        }
        return new FileSystemResource(p);
    }

    @Override
    public boolean exists(String storedFilename) {
        return Files.isRegularFile(resolve(storedFilename));
    }

    /** Deletes these files after the current transaction commits, or immediately when there is no transaction. */
    @Override
    public void deleteAfterCommit(Collection<String> storedFilenames) {
        if (storedFilenames == null || storedFilenames.isEmpty()) {
            return;
        }
        List<String> names = List.copyOf(storedFilenames);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    names.forEach(FileStorageServiceImpl.this::deleteQuietly);
                }
            });
        } else {
            names.forEach(this::deleteQuietly);
        }
    }

    /** Deletes a just-written file if the current transaction does not commit, so no orphan files are left. */
    @Override
    public void deleteOnRollback(String storedFilename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(storedFilename);
                }
            }
        });
    }

    void deleteQuietly(String storedFilename) {
        try {
            Files.deleteIfExists(resolve(storedFilename));
        } catch (IOException | RuntimeException e) {
            log.error("Failed to delete file (needs manual cleanup): {}", storedFilename, e);
        }
    }

    private Path resolve(String storedFilename) {
        if (storedFilename == null || !STORED_NAME.matcher(storedFilename).matches()) {
            throw new NotFoundException();
        }
        Path p = root.resolve(storedFilename).normalize();
        if (!p.startsWith(root)) {
            throw new NotFoundException();
        }
        return p;
    }

    /** Keeps only the bare filename: strips paths and control characters and limits the length (keeping the extension). */
    static String sanitizeFilename(String name) {
        if (name == null) {
            return "file";
        }
        String n = name;
        int slash = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        if (slash >= 0) {
            n = n.substring(slash + 1);
        }
        n = n.replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) {
            return "file";
        }
        if (n.length() > 200) {
            String ext = extensionOf(n);
            if (ext.length() > 10) {
                ext = "";
            }
            int keep = 200 - (ext.isEmpty() ? 0 : ext.length() + 1);
            n = n.substring(0, keep) + (ext.isEmpty() ? "" : "." + ext);
        }
        return n;
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
