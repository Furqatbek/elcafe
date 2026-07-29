package com.elcafe.service;

import com.elcafe.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Service for handling file uploads (images for products, menus, etc.)
 */
@Slf4j
@Service
public class FileUploadService {

    /** A folder is one plain path segment — no separators, no dots, so no traversal. */
    private static final java.util.regex.Pattern FOLDER_PATTERN =
            java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,64}");

    /** Raster images only. SVG is excluded: it is a script-capable document, not an inert image. */
    private static final java.util.Set<String> ALLOWED_EXTENSIONS =
            java.util.Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".avif");

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.file.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    /**
     * Upload a file and return the URL
     */
    public String uploadFile(MultipartFile file, String subfolder) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }

        // The subfolder arrives straight from a request parameter. Unvalidated it is a write-side path
        // traversal: "../../.." lets any OPERATOR drop a file anywhere the process can write, which is
        // an escape from the tenant and potentially from the app itself.
        String safeSubfolder = requireAllowedFolder(subfolder);
        // The stored extension decides how the file is served back. Anything outside this allowlist
        // (.html, .svg, .js ...) would be attacker-controlled content on our own origin — stored XSS
        // against a SPA that holds its JWT in localStorage.
        String extension = requireAllowedExtension(file);

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir, safeSubfolder);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename. The name is ours (a UUID) plus a validated extension — the
            // client-supplied filename is never used for the path.
            String filename = UUID.randomUUID().toString() + extension;

            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return URL
            String fileUrl = String.format("%s/%s/%s", baseUrl, subfolder, filename);
            log.info("File uploaded successfully: {}", fileUrl);
            return fileUrl;

        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a file
     */
    public void deleteFile(String fileUrl) {
        try {
            // Extract filename from URL
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            String subfolder = fileUrl.substring(baseUrl.length() + 1, fileUrl.lastIndexOf("/"));

            // The URL is caller-supplied, so the pieces carved out of it are too: without a containment
            // check this deletes any file the process can reach, not just an upload.
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = base.resolve(subfolder).resolve(filename).normalize();
            if (!filePath.startsWith(base)) {
                log.warn("Blocked delete outside the upload directory: {}", fileUrl);
                throw new BadRequestException("Invalid file URL");
            }

            Files.deleteIfExists(filePath);

            log.info("File deleted successfully: {}", fileUrl);

        } catch (IOException e) {
            log.error("Failed to delete file: {}", e.getMessage());
            throw new RuntimeException("Failed to delete file: " + e.getMessage(), e);
        }
    }

    /**
     * A folder must be a single, simple path segment. Anything with a separator or a "." component is
     * rejected outright rather than sanitised, so there is no encoding trick to smuggle a traversal
     * through. Deliberately a shape check and not a fixed enum — callers pass "products", "images" and
     * other per-feature names, and a shape check closes the hole without breaking them.
     */
    private String requireAllowedFolder(String subfolder) {
        if (subfolder == null || !FOLDER_PATTERN.matcher(subfolder).matches()) {
            throw new BadRequestException(
                    "Invalid upload folder: must be 1-64 characters of letters, digits, '-' or '_'");
        }
        return subfolder;
    }

    /**
     * Only image types we are willing to serve back inline. The extension is taken from the client's
     * filename, so it is attacker-controlled; allowing .html/.svg/.js here would put executable content
     * on our own origin.
     */
    private String requireAllowedExtension(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        int dot = originalFilename == null ? -1 : originalFilename.lastIndexOf('.');
        String extension = dot >= 0 ? originalFilename.substring(dot).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException(
                    "Unsupported file type. Allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        return extension;
    }
}
