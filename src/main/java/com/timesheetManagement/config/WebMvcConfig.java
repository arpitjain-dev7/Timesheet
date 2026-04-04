package com.timesheetManagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configures Spring MVC to serve uploaded files (e.g. profile photos)
 * directly from the local filesystem.
 *
 * <p>Without this, requests to {@code /uploads/**} would fail with
 * {@code NoResourceFoundException} because Spring's default static-resource
 * handler only looks inside the classpath / JAR, not on the filesystem.
 *
 * <p>URL pattern  : {@code /uploads/profile-photos/<filename>}
 * <p>Served from  : {@code <working-dir>/uploads/profile-photos/<filename>}
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * Root upload directory as configured in application.yaml.
     * e.g. "uploads/profile-photos"  →  parent = "uploads/"
     */
    @Value("${app.upload.dir:uploads/profile-photos}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        /*
         * Resolve the absolute path of the uploads root directory.
         * Paths.get(uploadDir).getParent() gives us the "uploads/" folder so
         * that the full relative URL /uploads/profile-photos/file.png maps
         * correctly to the file on disk.
         */
        Path uploadsRoot = Paths.get(uploadDir).getParent();
        if (uploadsRoot == null) {
            uploadsRoot = Paths.get("uploads");
        }

        String resourceLocation = "file:" + uploadsRoot.toAbsolutePath() + "/";

        log.info("[WebMvcConfig] Serving /uploads/** from filesystem path: {}", resourceLocation);

        registry
            .addResourceHandler("/uploads/**")
            .addResourceLocations(resourceLocation)
            .setCachePeriod(3600);          // cache in browser for 1 hour
    }
}

