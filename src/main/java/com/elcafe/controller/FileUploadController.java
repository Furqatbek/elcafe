package com.elcafe.controller;

import com.elcafe.service.FileUploadService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for file uploads
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "File Upload", description = "File upload APIs for images")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Upload file (image)")
    public ResponseEntity<ApiResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "products") String folder
    ) {
        String fileUrl = fileUploadService.uploadFile(file, folder);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded successfully", fileUrl));
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete file")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@RequestParam String fileUrl) {
        fileUploadService.deleteFile(fileUrl);
        return ResponseEntity.ok(ApiResponse.success("File deleted successfully", null));
    }
}
