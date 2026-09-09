package com.kalcpos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring Web configuration for CORS and security settings.
 * Centralizes configuration that was previously scattered across controllers.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Configure global CORS settings.
     * Applied to all endpoints unless overridden in specific controllers.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*") // Adjust based on security requirements
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * Create and manage BCryptPasswordEncoder as a Spring bean.
     * Centralizes password encoding to avoid creating multiple instances.
     *
     * @return BCryptPasswordEncoder instance
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
