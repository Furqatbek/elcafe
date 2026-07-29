package com.elcafe.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller for serving uploaded files (images, etc.)
 */
@Slf4j
@RestController
@RequestMapping("/uploads")
public class FileServeController {

    /**
     * Content types that may render inline. Deliberately raster-only: SVG is excluded because it is an
     * XML document that executes script when navigated to, which would make it a stored-XSS vehicle.
     */
    private static final java.util.Set<String> INLINE_SAFE_TYPES = java.util.Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif");

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String folder,
            @PathVariable String filename
    ) {
        try {
            Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = base.resolve(folder).resolve(filename).normalize();

            // Containment check. This endpoint is public (/uploads/** is permitAll) and {filename:.+}
            // matches slashes and dots, so without this a traversing path escapes the upload root and
            // turns image serving into unauthenticated arbitrary file read. normalize() alone does NOT
            // prevent that — it resolves "..", it does not reject it.
            if (!filePath.startsWith(base)) {
                log.warn("Blocked path traversal attempt: folder={}, filename={}", folder, filename);
                return ResponseEntity.notFound().build();
            }

            log.debug("Attempting to serve file: {}", filePath);

            if (!Files.exists(filePath)) {
                log.warn("File not found: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("File not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            // Determine content type
            String contentType;
            try {
                contentType = Files.probeContentType(filePath);
            } catch (IOException e) {
                contentType = "application/octet-stream";
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // Never let an uploaded file execute on our origin. Uploads are attacker-influenced content
            // served from the same host as the SPA, and the SPA keeps its JWT in localStorage — an
            // HTML/SVG file rendered inline here would be stored XSS with full session access. Anything
            // that is not a plain raster image is forced to download instead of render, and nosniff
            // stops the browser from second-guessing the declared type.
            boolean inlineSafe = INLINE_SAFE_TYPES.contains(contentType.toLowerCase());
            if (!inlineSafe) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000") // Cache for 1 year
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.CONTENT_DISPOSITION, inlineSafe ? "inline" : "attachment")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Malformed URL for file: {}/{}", folder, filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
