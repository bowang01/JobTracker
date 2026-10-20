package com.jobtrack.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Whitelist of uploadable file types. Checks both the extension and the file signature (magic bytes),
 * so an executable renamed to .pdf is rejected.
 */
public enum AllowedFileType {

    PDF("pdf", "application/pdf") {
        @Override
        public boolean contentMatches(byte[] b) {
            return startsWith(b, new byte[] {'%', 'P', 'D', 'F', '-'});
        }
    },
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document") {
        @Override
        public boolean contentMatches(byte[] b) {
            return startsWith(b, new byte[] {'P', 'K', 0x03, 0x04});
        }
    },
    DOC("doc", "application/msword") {
        @Override
        public boolean contentMatches(byte[] b) {
            return startsWith(b, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
        }
    },
    TXT("txt", "text/plain; charset=UTF-8") {
        @Override
        public boolean contentMatches(byte[] b) {
            for (byte x : b) {
                if (x == 0) {
                    return false;
                }
            }
            try {
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(b));
                return true;
            } catch (CharacterCodingException e) {
                return false;
            }
        }
    };

    public static final String DESCRIPTION = "PDF, DOC, DOCX, TXT (UTF-8)";

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
