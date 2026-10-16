package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ProductionHardeningSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================================
    // SECTION 1: PRODUCTION PROFILE CONFIGURATION AUDIT
    // =========================================================================

    @Test
    @DisplayName("Production profile configuration exists and enforces strict credentials from environment variables")
    public void testProductionConfigurationRequiresEnvironmentSecrets() throws Exception {
        ClassPathResource prodResource = new ClassPathResource("application-prod.properties");
        assertTrue(prodResource.exists(), "application-prod.properties must exist");

        Properties prodProps = new Properties();
        try (InputStream in = prodResource.getInputStream()) {
            prodProps.load(in);
        }

        // 1. Mandatory secrets must not have insecure default fallback values
        assertEquals("${DB_URL}", prodProps.getProperty("spring.datasource.url"),
                "Production DB URL must require ${DB_URL} without an insecure default");
        assertEquals("${DB_USERNAME}", prodProps.getProperty("spring.datasource.username"),
                "Production DB username must require ${DB_USERNAME} without an insecure default");
        assertEquals("${DB_PASSWORD}", prodProps.getProperty("spring.datasource.password"),
                "Production DB password must require ${DB_PASSWORD} without an insecure default");
        assertEquals("${MAIL_USERNAME}", prodProps.getProperty("spring.mail.username"),
                "Production mail username must require ${MAIL_USERNAME}");
        assertEquals("${MAIL_PASSWORD}", prodProps.getProperty("spring.mail.password"),
                "Production mail password must require ${MAIL_PASSWORD}");

        // 2. Demo user seeding must be disabled by default in production
        assertEquals("${SEED_DEMO_USERS:false}", prodProps.getProperty("app.security.seed-demo-users"),
                "Demo user seeding must default to false in production to prevent well-known credentials");

        // 3. Verbose SQL and stack trace logging must be suppressed
        assertEquals("false", prodProps.getProperty("spring.jpa.show-sql"),
                "Production must not log SQL queries to console");
        assertEquals("WARN", prodProps.getProperty("logging.level.org.hibernate.SQL"),
                "Production must set hibernate SQL logging to WARN");
        assertEquals("never", prodProps.getProperty("server.error.include-stacktrace"),
                "Production must never include stack traces in error responses");
        assertEquals("never", prodProps.getProperty("server.error.include-message"),
                "Production must never include internal error messages");
        assertEquals("false", prodProps.getProperty("server.error.whitelabel.enabled"),
                "Production must disable the Spring Boot whitelabel error page");

        // 4. Session & Cookie Security
        assertEquals("true", prodProps.getProperty("server.servlet.session.cookie.http-only"),
                "Production session cookie must be HttpOnly");
        assertEquals("${SESSION_COOKIE_SECURE:true}", prodProps.getProperty("server.servlet.session.cookie.secure"),
                "Production session cookie must default to secure");
        assertEquals("lax", prodProps.getProperty("server.servlet.session.cookie.same-site"),
                "Production session cookie must have SameSite=lax");

        // 5. Template caching & graceful shutdown
        assertEquals("true", prodProps.getProperty("spring.thymeleaf.cache"),
                "Production must enable Thymeleaf template caching for performance and security");
        assertEquals("graceful", prodProps.getProperty("server.shutdown"),
                "Production must enable graceful shutdown");
    }

    // =========================================================================
    // SECTION 2: SUPPLY CHAIN & DEPENDENCY CONFIGURATION
    // =========================================================================

    @Test
    @DisplayName("POM configuration: uses secure managed dependencies, packaging exclusions, and no untrusted repositories")
    public void testPomSupplyChainHardening() throws Exception {
        Path pomPath = Path.of("pom.xml");
        assertTrue(Files.exists(pomPath), "pom.xml must exist in project root");
        String pomContent = Files.readString(pomPath);

        // 1. mysql-connector-j must not use hardcoded vulnerable 8.0.33 version
        assertFalse(pomContent.contains("<version>8.0.33</version>"),
                "pom.xml must not pin outdated and vulnerable mysql-connector-j 8.0.33");

        // 2. ZXing library must be updated to 3.5.3+
        assertTrue(pomContent.contains("<version>3.5.3</version>"),
                "pom.xml should use zxing 3.5.3");

        // 3. Packaging exclusions must prevent sensitive files from leaking into the WAR
        assertTrue(pomContent.contains("<packagingExcludes>"),
                "pom.xml maven-war-plugin must declare packagingExcludes");
        assertTrue(pomContent.contains("**/.env*"),
                "packagingExcludes must exclude .env files");
        assertTrue(pomContent.contains("**/.git*"),
                "packagingExcludes must exclude .git files");

        // 4. No untrusted custom repositories or HTTP repository URLs
        assertFalse(pomContent.contains("<repositories>"),
                "pom.xml should rely on standard trusted Maven Central without custom repositories");
        assertFalse(pomContent.contains("<url>http://"),
                "pom.xml must not reference insecure plain HTTP repository URLs");
    }

    // =========================================================================
    // SECTION 3: ACTUATOR & MANAGEMENT INTERFACE HARDENING
    // =========================================================================

    @Test
    @DisplayName("Actuator & management interfaces: sensitive endpoints are not exposed")
    public void testManagementEndpointsExposureHardening() throws Exception {
        ClassPathResource prodResource = new ClassPathResource("application-prod.properties");
        Properties prodProps = new Properties();
        try (InputStream in = prodResource.getInputStream()) {
            prodProps.load(in);
        }

        String exposure = prodProps.getProperty("management.endpoints.web.exposure.include");
        if (exposure != null) {
            // If management endpoints are configured, only health and info should be exposed
            assertFalse(exposure.contains("*"), "Must not expose all actuator endpoints via wildcard (*)");
            assertFalse(exposure.contains("env"), "Must not expose /actuator/env");
            assertFalse(exposure.contains("heapdump"), "Must not expose /actuator/heapdump");
            assertFalse(exposure.contains("beans"), "Must not expose /actuator/beans");
            assertFalse(exposure.contains("shutdown"), "Must not expose /actuator/shutdown");
            assertEquals("never", prodProps.getProperty("management.endpoint.health.show-details"),
                    "Health endpoint must not show system details");
        }
    }

    // =========================================================================
    // SECTION 4: ERROR SANITIZATION & STACK TRACE SUPPRESSION
    // =========================================================================

    @Test
    @DisplayName("Error responses do not leak stack traces or internal implementation details")
    public void testErrorResponsesDoNotLeakStackTraces() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("user", new com.example.demo.model.User("admin@test.com", "pass", "Admin", "ADMIN"));
        session.setAttribute("userRole", "ADMIN");

        // Accessing a nonexistent endpoint or triggering an error should return a clean response without stack traces
        mockMvc.perform(get("/non-existent-endpoint-for-error-test").session(session))
                .andExpect(status().is4xxClientError())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertFalse(body.contains("Exception:"), "Response should not contain raw exception class names");
                    assertFalse(body.contains("at org.springframework"), "Response should not contain Spring stack traces");
                    assertFalse(body.contains("at com.example.demo"), "Response should not contain application stack traces");
                });
    }

    // =========================================================================
    // SECTION 5: SOFTWARE BILL OF MATERIALS (SBOM) VALIDATION
    // =========================================================================

    @Test
    @DisplayName("CycloneDX SBOM exists, is valid JSON, and catalogs application components")
    public void testCycloneDxSbomValidation() throws Exception {
        // Check either target/bom.json or artifact copy
        File bomFile = new File("target/bom.json");
        if (!bomFile.exists()) {
            // Check in artifacts directory
            File artifactDir = new File("C:/Users/kisho/.gemini/antigravity/brain/18d68ff3-17bd-43cd-a313-9023b36c07d3");
            bomFile = new File(artifactDir, "bom.json");
        }

        assertTrue(bomFile.exists(), "CycloneDX SBOM file (bom.json) must be generated and present");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(bomFile);

        assertEquals("CycloneDX", root.path("bomFormat").asText(), "SBOM format must be CycloneDX");
        assertNotNull(root.path("specVersion").asText(), "specVersion must be present");
        assertTrue(root.path("components").isArray(), "components array must be present");
        assertTrue(root.path("components").size() > 50, "SBOM must catalog all direct and transitive dependencies");

        // Verify key components are cataloged
        boolean hasSpringBoot = false;
        boolean hasSpringSecurity = false;
        boolean hasZxing = false;
        boolean hasMysql = false;

        for (JsonNode component : root.path("components")) {
            String name = component.path("name").asText();
            if (name.contains("spring-boot")) hasSpringBoot = true;
            if (name.contains("spring-security-crypto")) hasSpringSecurity = true;
            if (name.contains("core") && component.path("group").asText().contains("zxing")) hasZxing = true;
            if (name.contains("mysql-connector-j")) hasMysql = true;
        }

        assertTrue(hasSpringBoot, "SBOM must include Spring Boot components");
        assertTrue(hasSpringSecurity, "SBOM must include Spring Security Crypto");
        assertTrue(hasZxing, "SBOM must include ZXing");
        assertTrue(hasMysql, "SBOM must include MySQL Connector");
    }
}
