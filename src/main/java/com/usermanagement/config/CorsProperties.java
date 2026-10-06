package com.usermanagement.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Origins allowed to call the API from a browser. Empty means same-origin only. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream().filter(origin -> origin != null && !origin.isBlank()).toList();
    }
}
