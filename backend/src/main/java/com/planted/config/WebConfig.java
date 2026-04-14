package com.planted.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.util.List;

@Configuration
@EnableConfigurationProperties(PlantedCorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    @Value("${planted.storage.local-path:./data/images}")
    private String localImageBasePath;

    private final PlantedCorsProperties corsProperties;

    public WebConfig(PlantedCorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> patterns = corsProperties.allowedOriginPatterns();
        String[] originPatterns = (patterns == null || patterns.isEmpty())
                ? new String[] {"http://localhost:3000", "https://*.vercel.app"}
                : patterns.toArray(String[]::new);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(originPatterns)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(localImageBasePath).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:" + absolutePath + "/");
    }
}
