package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

        @org.springframework.beans.factory.annotation.Value("${file.upload-dir:uploads}")
        private String uploadDir;

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

                // Add resource handler for uploaded task files - External Directory
                String uploadAbsolutePath = java.nio.file.Paths.get(uploadDir).toFile().getAbsolutePath();
                registry.addResourceHandler("/uploads/**")
                                .addResourceLocations("file:" + uploadAbsolutePath + "/", "classpath:/static/uploads/");
        }
}
