package com.timesheetManagement.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Binds {@code app.cors.*} from application.yaml.
 *
 * <p>Using {@code @ConfigurationProperties} (not {@code @Value}) because Spring's
 * {@code @Value} cannot bind a YAML sequence ({@code - item}) to {@code List<String>}.
 */
@Component
@ConfigurationProperties(prefix = "app.cors")
@Getter
@Setter
public class CorsProperties {

    /**
     * Origins allowed to call the API.
     * Configured in application.yaml under {@code app.cors.allowed-origins}.
     */
    private List<String> allowedOrigins = List.of("http://localhost:5173");
}

