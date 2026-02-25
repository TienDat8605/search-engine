package com.searchengine.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // index.html: never cache so browsers always get the latest version
        registry.addResourceHandler("/", "/index.html")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache().mustRevalidate());

        // Versioned assets (app.js?v=X, etc.): cache aggressively
        registry.addResourceHandler("/*.js", "/*.css")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)));
    }
}
