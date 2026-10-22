package com.jobtrack.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Whitelist of uploadable file types: PDF only, so every document can be viewed in the browser.
 * Checks both the extension and the file signature (magic bytes), so an executable renamed to .pdf is rejected.
 * Documents uploaded before this restriction (DOC, DOCX, TXT) can still be downloaded.
 */
public enum AllowedFileType {

    PDF("pdf", "application/pdf") {
        @Override
        public boolean contentMatches(byte[] b) {
            return startsWith(b, new byte[] {'%', 'P', 'D', 'F', '-'});
        }
    };
    public static final String DESCRIPTION = "PDF";

    /** Value for the HTML accept attribute of file inputs. */
    public static final String ACCEPT = ".pdf,application/pdf";

    private final String extension;
    private final String contentType;

    AllowedFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public abstract boolean contentMatches(byte[] bytes);

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<AllowedFileType> fromExtension(String ext) {
        if (ext == null) {
            return Optional.empty();
        }
        String e = ext.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(t -> t.extension.equals(e)).findFirst();
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
