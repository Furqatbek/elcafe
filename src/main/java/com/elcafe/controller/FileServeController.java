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

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String folder,
            @PathVariable String filename
    ) {
        try {
            Path filePath = Paths.get(uploadDir, folder, filename).toAbsolutePath().normalize();

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

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=31536000") // Cache for 1 year
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Malformed URL for file: {}/{}", folder, filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
