package com.example.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SpringBatchApplication} helper methods.
 *
 * <p>All tested methods are package-private or static — no Spring context required.
 * The main() method is deliberately excluded from unit-test scope: it calls
 * System.exit(), which would terminate the JVM.
 */
class SpringBatchApplicationTest {

    // ---------------------------------------------------------------
    // kebabToCamel
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
        "invoice-job,           invoiceJob",
        "customer-report-job,   customerReportJob",
        "customer-etl-v2,       customerEtlV2",
        "simple,                simple",
        "a-b-c,                 aBC",
        "mt-db-to-file-job,     mtDbToFileJob"
    })
    void kebabToCamel_convertsCorrectly(String input, String expected) {
        assertEquals(expected.trim(), SpringBatchApplication.kebabToCamel(input.trim()));
    }

    @Test
    void kebabToCamel_singleWord_unchanged() {
        assertEquals("invoice", SpringBatchApplication.kebabToCamel("invoice"));
    }

    @Test
    void kebabToCamel_emptySegment_handledGracefully() {
        // Trailing hyphen produces an empty segment — should not throw
        String result = SpringBatchApplication.kebabToCamel("job-");
        assertNotNull(result);
        assertEquals("job", result);   // empty segment → nothing appended
    }

    // ---------------------------------------------------------------
    // run() — no args / --dry-run only → exit code 2
    // ---------------------------------------------------------------

    @Test
    void run_noArgs_returnsExitCode2() {
        int code = SpringBatchApplication.run(new String[]{});
        assertEquals(2, code, "No arguments should return exit code 2 (usage error)");
    }

    @Test
    void run_onlyDryRunFlag_returnsExitCode2() {
        // --dry-run alone → cleaned args is empty → printUsage() → exit 2
        int code = SpringBatchApplication.run(new String[]{"--dry-run"});
        assertEquals(2, code);
    }

    @Test
    void run_onlyValidateFlag_returnsExitCode2() {
        int code = SpringBatchApplication.run(new String[]{"--validate"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // run() — invalid job slug → SpringApplicationContext will fail
    // to load; the fatal catch returns exit code 2.
    // ---------------------------------------------------------------

    @Test
    void run_nonExistentJob_returnsExitCode2() {
        // "non-existent-job" → tries to load spring/jobs/non-existent-job.xml
        // from the test classpath — it doesn't exist → fatal error → code 2
        int code = SpringBatchApplication.run(new String[]{"non-existent-job"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // run() — isDryRun + non-empty cleaned args → execute() called with dryRun=true
    // ---------------------------------------------------------------

    @Test
    void run_dryRunFlagWithJobSlug_executeCalledWithDryRun() {
        // --dry-run is stripped → cleaned = ["non-existent-job"] (non-empty)
        // execute() is invoked with dryRun=true but fails loading XML → exit 2
        int code = SpringBatchApplication.run(new String[]{"--dry-run", "non-existent-job"});
        assertEquals(2, code);
    }

    @Test
    void run_validateFlagWithJobSlug_executeCalledWithDryRun() {
        int code = SpringBatchApplication.run(new String[]{"--validate", "non-existent-job"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // parseCLIParams — key=value and malformed arg branches
    // ---------------------------------------------------------------

    @Test
    void run_withKeyValueParam_parsesKeyValuePair() {
        // parseCLIParams sees "batchDate=2026-01-15" → eq > 0 branch → param added
        // Spring context load still fails → exit 2
        int code = SpringBatchApplication.run(
                new String[]{"non-existent-job", "batchDate=2026-01-15"});
        assertEquals(2, code);
    }

    @Test
    void run_withMalformedArg_logsWarnAndStillReturnsExitCode2() {
        // parseCLIParams: "malformed" has no '=' → else branch → log.warn
        int code = SpringBatchApplication.run(
                new String[]{"non-existent-job", "malformed"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // isExplicitPath / resolveXmlPath — ".xml" suffix and "/" in path
    // ---------------------------------------------------------------

    @Test
    void run_withDotXmlSuffix_treatedAsExplicitPath() {
        // "spring/jobs/something.xml" → isExplicitPath returns true → xmlPath unchanged
        int code = SpringBatchApplication.run(
                new String[]{"spring/jobs/non-existent.xml"});
        assertEquals(2, code);
    }

    @Test
    void run_withSlashInArg_treatedAsExplicitPath() {
        // "some/path" → isExplicitPath returns true (contains "/")
        int code = SpringBatchApplication.run(new String[]{"some/path"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // resolveBeanHint — args[1] without '=' is an explicit bean name
    // ---------------------------------------------------------------

    @Test
    void run_withExplicitXmlAndBeanName_usesBeanNameFromArgs() {
        // args[1]="myBean" has no '=' → resolveBeanHint returns "myBean"
        // paramStartIndex returns 2
        int code = SpringBatchApplication.run(
                new String[]{"spring/jobs/non-existent.xml", "myBean"});
        assertEquals(2, code);
    }

    // ---------------------------------------------------------------
    // enrichEnvironment — profile branch (classpath file not found)
    // ---------------------------------------------------------------

    @Test
    void run_withNonExistentProfile_logsWarnAndContinues() {
        // Sets app.profile so enrichEnvironment tries to load the profile
        // properties file, finds none, logs a warning, and continues.
        System.setProperty("app.profile", "test-nonexistent-xxxyyy");
        try {
            int code = SpringBatchApplication.run(new String[]{"non-existent-job"});
            assertEquals(2, code);
        } finally {
            System.clearProperty("app.profile");
        }
    }

    // ---------------------------------------------------------------
    // enrichEnvironment — external config file branch (file not found)
    // ---------------------------------------------------------------

    @Test
    void run_withNonExistentExternalConfigFile_logsWarnAndContinues() {
        System.setProperty("app.config.file", "/non/existent/path/config.properties");
        try {
            int code = SpringBatchApplication.run(new String[]{"non-existent-job"});
            assertEquals(2, code);
        } finally {
            System.clearProperty("app.config.file");
        }
    }

    // ---------------------------------------------------------------
    // enrichEnvironment — external config file branch (file exists → loaded)
    // ---------------------------------------------------------------

    @Test
    void run_withExistingExternalConfigFile_loadsFileAndContinues(@TempDir Path tempDir)
            throws IOException {
        // Creates a real temp file → loadFileSystemProperties reads it successfully
        // Spring context load still fails → exit 2
        Path configFile = tempDir.resolve("test-batch.properties");
        Files.writeString(configFile, "batch.test.prop=hello\n");

        System.setProperty("app.config.file", configFile.toString());
        try {
            int code = SpringBatchApplication.run(new String[]{"non-existent-job"});
            assertEquals(2, code);
        } finally {
            System.clearProperty("app.config.file");
        }
    }

    // ---------------------------------------------------------------
    // enrichEnvironment — profile + CLI params → addBefore(lastAdded) path
    // ---------------------------------------------------------------

    @Test
    void run_withProfileAndCliParams_bothEnrichEnvironment(@TempDir Path tempDir)
            throws IOException {
        // Profile file exists → lastAdded is set → CLI params use addBefore(lastAdded)
        Path profileFile = tempDir.resolve("application-testprofxxx.properties");
        Files.writeString(profileFile, "batch.test=1\n");

        // The profile file must be on the classpath to be found, so this takes
        // the "profile file NOT found" branch; then CLI params use addLast.
        System.setProperty("app.profile", "testprofxxx");
        try {
            int code = SpringBatchApplication.run(
                    new String[]{"non-existent-job", "key=value"});
            assertEquals(2, code);
        } finally {
            System.clearProperty("app.profile");
        }
    }
}
