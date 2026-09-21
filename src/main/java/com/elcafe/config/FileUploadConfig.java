package com.elcafe.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class FileUploadConfig implements WebMvcConfigurer {

    @Value("${app.file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            // Resolve upload path and create directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created upload directory: {}", uploadPath);
            }

            // Convert to file URL with trailing slash
            String resourceLocation = "file:" + uploadPath.toString() + "/";

            log.info("Configuring resource handler: /uploads/** -> {}", resourceLocation);

            registry.addResourceHandler("/uploads/**")
                    .addResourceLocations(resourceLocation);

        } catch (IOException e) {
            log.error("Failed to configure file upload directory: {}", e.getMessage());
            throw new RuntimeException("Failed to configure file upload directory", e);
        }
    }
}
