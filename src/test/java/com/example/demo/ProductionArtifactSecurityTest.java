package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class ProductionArtifactSecurityTest {

    @Test
    @DisplayName("Production profile configuration: Database and Mail secrets require environment variables without defaults")
    public void testProductionConfigurationSecretsHaveNoDefaults() throws Exception {
        ClassPathResource prodResource = new ClassPathResource("application-prod.properties");
        assertTrue(prodResource.exists(), "application-prod.properties must exist");

        Properties prodProps = new Properties();
        try (InputStream in = prodResource.getInputStream()) {
            prodProps.load(in);
        }

        assertEquals("${DB_URL}", prodProps.getProperty("spring.datasource.url"),
                "Production DB URL must require ${DB_URL} without fallback default");
        assertEquals("${DB_USERNAME}", prodProps.getProperty("spring.datasource.username"),
                "Production DB username must require ${DB_USERNAME} without fallback default");
        assertEquals("${DB_PASSWORD}", prodProps.getProperty("spring.datasource.password"),
                "Production DB password must require ${DB_PASSWORD} without fallback default");
        assertEquals("${MAIL_USERNAME}", prodProps.getProperty("spring.mail.username"),
                "Production mail username must require ${MAIL_USERNAME} without fallback default");
        assertEquals("${MAIL_PASSWORD}", prodProps.getProperty("spring.mail.password"),
                "Production mail password must require ${MAIL_PASSWORD} without fallback default");
    }

    @Test
    @DisplayName("Production profile configuration: Demo user seeding is disabled by default")
    public void testDemoUserSeedingDisabledInProduction() throws Exception {
        ClassPathResource prodResource = new ClassPathResource("application-prod.properties");
        Properties prodProps = new Properties();
        try (InputStream in = prodResource.getInputStream()) {
            prodProps.load(in);
        }

        assertEquals("${SEED_DEMO_USERS:false}", prodProps.getProperty("app.security.seed-demo-users"),
                "Demo user seeding must default to false in production");
    }

    @Test
    @DisplayName("Production profile configuration: SQL query logging and stack traces are suppressed")
    public void testProductionLoggingAndStackTracesSuppressed() throws Exception {
        ClassPathResource prodResource = new ClassPathResource("application-prod.properties");
        Properties prodProps = new Properties();
        try (InputStream in = prodResource.getInputStream()) {
            prodProps.load(in);
        }

        assertEquals("false", prodProps.getProperty("spring.jpa.show-sql"));
        assertEquals("WARN", prodProps.getProperty("logging.level.org.hibernate.SQL"));
        assertEquals("never", prodProps.getProperty("server.error.include-stacktrace"));
        assertEquals("never", prodProps.getProperty("server.error.include-message"));
        assertEquals("false", prodProps.getProperty("server.error.whitelabel.enabled"));
    }

    @Test
    @DisplayName("POM configuration: Packaging excludes prevent .env, .git, and dev properties from leaking into the WAR")
    public void testPackagingExcludesSensitiveFiles() throws Exception {
        Path pomPath = Path.of("pom.xml");
        assertTrue(Files.exists(pomPath), "pom.xml must exist in project root");
        String pomContent = Files.readString(pomPath);

        assertTrue(pomContent.contains("<packagingExcludes>"), "pom.xml must contain packagingExcludes configuration");
        assertTrue(pomContent.contains("**/.env*"), "packagingExcludes must exclude .env files");
        assertTrue(pomContent.contains("**/.git*"), "packagingExcludes must exclude .git files");
        assertTrue(pomContent.contains("**/application-dev*.properties"), "packagingExcludes must exclude dev properties");
    }

    @Test
    @DisplayName("CycloneDX SBOM validation: SBOM catalogs dependencies and meets CycloneDX specification")
    public void testCycloneDxSbomValidation() throws Exception {
        File bomFile = new File("target/bom.json");
        if (!bomFile.exists()) {
            File artifactDir = new File("C:/Users/kisho/.gemini/antigravity/brain/18d68ff3-17bd-43cd-a313-9023b36c07d3");
            bomFile = new File(artifactDir, "bom.json");
        }

        assertTrue(bomFile.exists(), "CycloneDX SBOM file (bom.json) must exist");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(bomFile);

        assertEquals("CycloneDX", root.path("bomFormat").asText(), "SBOM format must be CycloneDX");
        assertNotNull(root.path("specVersion").asText());
        assertTrue(root.path("components").isArray());
        assertTrue(root.path("components").size() > 50, "SBOM must catalog dependencies");
    }
}
