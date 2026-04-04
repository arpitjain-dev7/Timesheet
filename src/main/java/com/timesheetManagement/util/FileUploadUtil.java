package com.timesheetManagement.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class FileUploadUtil {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private static final long MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    @Value("${app.upload.dir:uploads/profile-photos}")
    private String uploadDir;

    /**
     * Saves the uploaded photo to the local filesystem.
     *
     * @param username owning user (used as filename prefix for readability)
     * @param file     the uploaded MultipartFile
     * @return relative path stored in DB  (e.g. "uploads/profile-photos/john_abc123.png")
     */
    public String savePhoto(String username, MultipartFile file) throws IOException {
        validateFile(file);

        // Ensure directory exists
        Path uploadPath = Paths.get(uploadDir);
        Files.createDirectories(uploadPath);

        // Build a unique filename:  username_<uuid>.<ext>
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo");
        String extension = getExtension(originalFilename);
        String storedFilename = username + "_" + UUID.randomUUID() + extension;

        Path targetPath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Saved profile photo for user '{}' → {}", username, targetPath);
        return uploadDir + "/" + storedFilename;
    }

    /**
     * Deletes a previously stored file (best-effort, no exception if missing).
     */
    public void deletePhoto(String filePath) {
        if (filePath == null || filePath.isBlank()) return;
        try {
            Path path = Paths.get(filePath);
            if (Files.deleteIfExists(path)) {
                log.info("Deleted photo: {}", filePath);
            }
        } catch (IOException e) {
            log.warn("Could not delete photo at {}: {}", filePath, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds the 5 MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "Invalid file type. Allowed: JPEG, PNG, GIF, WEBP");
        }
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0) ? filename.substring(dotIndex) : "";
    }
}

