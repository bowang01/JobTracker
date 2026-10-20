package com.jobtrack.service;

public record StoredFile(String storedFilename, String originalFilename, String contentType,
                         long sizeBytes, String sha256) {
}
