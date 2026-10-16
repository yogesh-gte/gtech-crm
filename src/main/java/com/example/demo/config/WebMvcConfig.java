package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        @org.springframework.beans.factory.annotation.Value("${file.upload-dir:uploads}")
        private String uploadDir;

        @org.springframework.beans.factory.annotation.Value("${security.cors.allowed-origins:}")
        private String corsAllowedOrigins;

        @org.springframework.beans.factory.annotation.Autowired
        private AuthorizationInterceptor authorizationInterceptor;

        @Override
        public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
                if (corsAllowedOrigins != null && !corsAllowedOrigins.trim().isEmpty()) {
                        String[] origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty() && !s.equals("*"))
                                        .toArray(String[]::new);

                        if (origins.length > 0) {
                                registry.addMapping("/**")
                                                .allowedOrigins(origins)
                                                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                                                .allowedHeaders("Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "Authorization", "Accept", "X-Requested-With")
                                                .allowCredentials(true)
                                                .maxAge(3600);
                        }
                }
        }

        @Override
        public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                registry.addInterceptor(authorizationInterceptor)
                                .addPathPatterns("/**")
                                .excludePathPatterns(
                                                "/static/**",
                                                "/css/**",
                                                "/js/**",
                                                "/images/**",
                                                "/favicon.ico"
                                );
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {
                // Configure static resource handling for external Tomcat deployment
                registry.addResourceHandler("/static/**")
                                .addResourceLocations("classpath:/static/");
                registry.addResourceHandler("/css/**")
                                .addResourceLocations("classpath:/static/css/");
                registry.addResourceHandler("/js/**")
                                .addResourceLocations("classpath:/static/js/");
                registry.addResourceHandler("/images/**")
                                .addResourceLocations("classpath:/static/images/");
        }
}
