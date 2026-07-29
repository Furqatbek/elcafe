package com.elcafe.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * /uploads/** is public and the filename path variable matches slashes, so the containment check is the
 * only thing between image serving and unauthenticated arbitrary file read.
 */
class FileServeControllerTest {

    @TempDir Path tempDir;

    private FileServeController controller;
    private Path secret;

    @BeforeEach
    void setUp() throws Exception {
        controller = new FileServeController();
        Path uploads = tempDir.resolve("uploads");
        Files.createDirectories(uploads.resolve("products"));
        ReflectionTestUtils.setField(controller, "uploadDir", uploads.toString());

        // A file that exists next to (but outside) the upload root — the traversal target.
        secret = tempDir.resolve("application-secrets.txt");
        Files.writeString(secret, "DB_PASSWORD=hunter2");

        Files.write(uploads.resolve("products").resolve("real.png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    @DisplayName("a traversing filename cannot escape the upload directory")
    void traversal_isBlocked() {
        ResponseEntity<Resource> response =
                controller.serveFile("products", "../../application-secrets.txt");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNull();
        assertThat(secret).exists(); // untouched
    }

    @Test
    @DisplayName("a traversing folder cannot escape the upload directory either")
    void traversalViaFolder_isBlocked() {
        ResponseEntity<Resource> response = controller.serveFile("..", "application-secrets.txt");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("a legitimate image is still served, inline and with nosniff")
    void realImage_isServed() {
        ResponseEntity<Resource> response = controller.serveFile("products", "real.png");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline");
    }

    @Test
    @DisplayName("a non-image that somehow exists is forced to download, never rendered")
    void nonImage_isForcedToDownload() throws Exception {
        Path uploads = Path.of((String) ReflectionTestUtils.getField(controller, "uploadDir"));
        Files.writeString(uploads.resolve("products").resolve("legacy.html"),
                "<script>alert(document.cookie)</script>");

        ResponseEntity<Resource> response = controller.serveFile("products", "legacy.html");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("attachment");
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/octet-stream");
    }
}
