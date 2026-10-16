package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Security Verification Tests:
 * Secrets and Repository Security
 */
public class SecretAndRepositorySecurityTest {

    private static final Path ROOT_DIR = Path.of("").toAbsolutePath();

    /**
     * Requirement A & D:
     * Scan working tree and configuration files for exposed secrets.
     */
    @Test
    void testSecretScanningOnWorkingTreeFiles() throws IOException {
        List<Path> filesToScan = List.of(
                ROOT_DIR.resolve(".env.example"),
                ROOT_DIR.resolve("src/main/resources/application.properties"),
                ROOT_DIR.resolve("src/main/resources/application-dev-h2.properties"),
                ROOT_DIR.resolve("src/main/resources/application-prod.properties"),
                ROOT_DIR.resolve("src/test/resources/application.properties"),
                ROOT_DIR.resolve("tomcat-users-template.xml")
        );

        // Regex patterns for secrets
        Pattern appPasswordPattern = Pattern.compile("(?i)(password\\s*=\\s*)([a-z]{4}\\s?[a-z]{4}\\s?[a-z]{4}\\s?[a-z]{4})");
        Pattern privateKeyPattern = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");

        // Specific signatures of previously identified credentials (sanitized verification)
        Pattern exposedAppPwdPattern1 = Pattern.compile("bvjk\\s?fkui\\s?ulcg\\s?jejp");
        Pattern exposedAppPwdPattern2 = Pattern.compile("ewum\\s?nftv\\s?pjkc\\s?kvbb");
        Pattern exposedPersonalEmail1 = Pattern.compile("kishorhavanur07@gmail\\.com");
        Pattern exposedPersonalEmail2 = Pattern.compile("suchisuchithra184@gmail\\.com");
        Pattern hardcodedAdmin123 = Pattern.compile("password=\"admin123\"");

        List<String> findings = new ArrayList<>();

        for (Path file : filesToScan) {
            if (!Files.exists(file)) continue;

            String content = Files.readString(file);
            String filename = file.getFileName().toString();

            if (privateKeyPattern.matcher(content).find()) {
                findings.add(filename + ": Found Private Key block");
            }
            if (exposedAppPwdPattern1.matcher(content).find() || exposedAppPwdPattern2.matcher(content).find()) {
                findings.add(filename + ": Found previously exposed Gmail App Password");
            }
            if (exposedPersonalEmail1.matcher(content).find() || exposedPersonalEmail2.matcher(content).find()) {
                findings.add(filename + ": Found exposed personal Gmail address in configuration");
            }
            if (hardcodedAdmin123.matcher(content).find()) {
                findings.add(filename + ": Found hardcoded admin123 password in template");
            }

            // Ensure no hardcoded raw 16-character app passwords exist as values
            Matcher m = appPasswordPattern.matcher(content);
            while (m.find()) {
                String val = m.group(2).replace(" ", "");
                if (val.length() == 16 && !val.equals("dev_dummy_passw") && !val.equals("test_dummy_passw")) {
                    findings.add(filename + ": Potential 16-character app password pattern detected");
                }
            }
        }

        assertTrue(findings.isEmpty(), "Secret scanning found unresolved exposed credentials: " + findings);
    }

    /**
     * Requirement C:
     * Missing required production secrets must cause a safe startup failure (fail-fast),
     * rather than silently falling back to insecure hardcoded defaults.
     */
    @Test
    void testProductionProfileFailsFastOnMissingSecrets() {
        assertThrows(Exception.class, () -> {
            new SpringApplicationBuilder(LogSphereApplication.class)
                    .profiles("prod")
                    .properties(
                            "spring.main.banner-mode=off",
                            "spring.main.web-application-type=none"
                    )
                    .run();
        }, "Application must fail to start in 'prod' profile when required environment variables (e.g. DB_PASSWORD, DB_URL) are missing");
    }

    /**
     * Requirement B:
     * Application starts successfully with valid development / test environment configuration.
     */
    @Test
    void testApplicationStartsSuccessfullyWithSafeTestConfig() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(LogSphereApplication.class)
                .profiles("test")
                .properties(
                        "spring.main.banner-mode=off",
                        "spring.main.web-application-type=none",
                        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.jpa.hibernate.ddl-auto=create-drop",
                        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
                        "app.security.seed-demo-users=false"
                )
                .run()) {
            assertNotNull(context, "Application context should load with safe test config");
            assertTrue(context.isActive(), "Application context should be active");
        }
    }

    /**
     * Requirement D:
     * Verify that .env.example contains placeholders only and no real secrets.
     */
    @Test
    void testEnvExampleContainsPlaceholdersOnly() throws IOException {
        Path envExample = ROOT_DIR.resolve(".env.example");
        assertTrue(Files.exists(envExample), ".env.example must exist in repository root");

        String content = Files.readString(envExample);
        assertFalse(content.contains("bvjkfkuiulcgjejp"), ".env.example must not contain real credentials");
        assertFalse(content.contains("ewum nftv pjkc kvbb"), ".env.example must not contain real credentials");
        assertFalse(content.contains("kishorhavanur07"), ".env.example must not contain personal email addresses");
        assertFalse(content.contains("suchisuchithra184"), ".env.example must not contain personal email addresses");

        assertTrue(content.contains("DB_PASSWORD=your_secure_db_password"), ".env.example must contain documented DB_PASSWORD placeholder");
        assertTrue(content.contains("MAIL_PASSWORD=your_smtp_app_password"), ".env.example must contain documented MAIL_PASSWORD placeholder");
        assertTrue(content.contains("SEED_DEMO_USERS=false"), ".env.example must document SEED_DEMO_USERS");
    }

    /**
     * Requirement 3:
     * Verify .gitignore rules cover .env, bin/, keys, logs, and local configuration.
     */
    @Test
    void testGitIgnoreCoversSensitivePatterns() throws IOException {
        Path gitIgnore = ROOT_DIR.resolve(".gitignore");
        assertTrue(Files.exists(gitIgnore), ".gitignore must exist");

        String content = Files.readString(gitIgnore);
        assertTrue(content.contains("bin/"), ".gitignore must ignore bin/");
        assertTrue(content.contains(".env"), ".gitignore must ignore .env files");
        assertTrue(content.contains("*.pem") || content.contains("*.key"), ".gitignore must ignore private key files");
        assertTrue(content.contains("*.log"), ".gitignore must ignore log files");
        assertTrue(content.contains("src/main/resources/application.properties"), ".gitignore must ignore local application.properties");
    }
}
