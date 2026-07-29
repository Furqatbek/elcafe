package com.elcafe.service;

import com.elcafe.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uploads are attacker-influenced content written to disk and served back from our own origin, so the
 * two things that must hold are: the write cannot escape the upload directory, and the stored file can
 * never be something the browser will execute (.html/.svg/.js).
 */
class FileUploadServiceTest {

    @TempDir Path tempDir;

    private FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "baseUrl", "http://localhost:8080/uploads");
    }

    private MockMultipartFile file(String originalName) {
        return new MockMultipartFile("file", originalName, "image/png", "not-really-a-png".getBytes());
    }

    @Test
    @DisplayName("a normal image upload is stored under the requested folder with a generated name")
    void upload_validImage_stored() {
        String url = service.uploadFile(file("holiday photo.PNG"), "products");

        assertThat(url).startsWith("http://localhost:8080/uploads/products/").endsWith(".png");
        assertThat(tempDir.resolve("products")).exists();
        // The client's filename is never reused for the stored path.
        assertThat(url).doesNotContain("holiday");
    }

    @Test
    @DisplayName("a traversing folder is rejected — no write outside the upload root")
    void upload_traversingFolder_rejected() {
        assertThatThrownBy(() -> service.uploadFile(file("a.png"), "../../../../tmp"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadFile(file("a.png"), "products/../.."))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadFile(file("a.png"), ".."))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("executable content types are rejected — stored XSS on our own origin")
    void upload_executableTypes_rejected() {
        assertThatThrownBy(() -> service.uploadFile(file("payload.html"), "products"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadFile(file("payload.svg"), "products"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.uploadFile(file("payload.js"), "products"))
                .isInstanceOf(BadRequestException.class);
        // No extension at all was previously stored as a bare UUID.
        assertThatThrownBy(() -> service.uploadFile(file("payload"), "products"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("deleting outside the upload directory is refused")
    void delete_outsideUploadDir_rejected() throws Exception {
        Path outside = tempDir.getParent().resolve("victim-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "do not delete me");
        try {
            assertThatThrownBy(() -> service.deleteFile(
                    "http://localhost:8080/uploads/../../" + outside.getFileName()))
                    .isInstanceOf(BadRequestException.class);
            assertThat(outside).exists();
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    @DisplayName("deleting a real upload still works")
    void delete_ownUpload_works() {
        String url = service.uploadFile(file("a.png"), "products");
        String stored = url.substring(url.lastIndexOf('/') + 1);

        service.deleteFile(url);

        assertThat(tempDir.resolve("products").resolve(stored)).doesNotExist();
    }
}
