package com.example.demo.util;

import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ContextPathUtil {
    
    @Autowired(required = false)
    private ServletContext servletContext;
    
    /**
     * Get the context path dynamically.
     * When deployed to external Tomcat, this will get the actual context path from the servlet context.
     * When running embedded, it will return the configured context path or "/".
     */
    public String getContextPath() {
        if (servletContext != null) {
            String contextPath = servletContext.getContextPath();
            return contextPath != null && !contextPath.isEmpty() ? contextPath : "";
        }
        return "";
    }
}

