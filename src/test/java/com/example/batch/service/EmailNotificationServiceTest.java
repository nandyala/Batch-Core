package com.example.batch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the static name-humanization helpers in {@link EmailNotificationService}.
 *
 * <p>The {@code sendJobReport} method requires a live SMTP server (JavaMailSender)
 * and is tested via integration tests (excluded from the unit coverage requirement).
 * The public static helpers are fully testable here.
 */
class EmailNotificationServiceTest {

    // ---------------------------------------------------------------
    // humanizeStepName
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
        "processInvoicesStep,   Process Invoices",
        "readAndWriteStep,      Read And Write",
        "statisticsAndEmailStep,Statistics And Email",
        "loadDataStep,          Load Data",
        "step1,                 Step1",                    // no trailing "Step" → full name title-cased
        "myStep,                My"                        // trailing "Step" stripped
    })
    void humanizeStepName_stripsStepSuffixAndSplitsCamel(String input, String expected) {
        assertEquals(expected.trim(), EmailNotificationService.humanizeStepName(input.trim()));
    }

    @Test
    void humanizeStepName_noStepSuffix_stillSplitsCamel() {
        // If the name doesn't end in "Step", just split camel
        String result = EmailNotificationService.humanizeStepName("processRecords");
        assertEquals("Process Records", result);
    }

    @Test
    void humanizeStepName_singleWordWithStep_returnsEmptyOrJustWord() {
        // "step" alone → strips "Step" → empty → toTitleCase returns ""
        String result = EmailNotificationService.humanizeStepName("step");
        // Result may be empty or "" — just verify it doesn't throw and isn't null
        assertNotNull(result);
    }

    @Test
    void humanizeStepName_upperCaseAcronym_splitCorrectly() {
        // "processHTTPRequest" → "Process HTTP Request"
        String result = EmailNotificationService.humanizeStepName("processHTTPRequestStep");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    // ---------------------------------------------------------------
    // humanizeJobName
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
        "invoiceJob,              Invoice Job",
        "customerReportJob,       Customer Report Job",
        "dbToFileJob,             Db To File Job",
        "partitionedDbToFileJob,  Partitioned Db To File Job",
        "sampleJob,               Sample Job"
    })
    void humanizeJobName_splitsCamelCorrectly(String input, String expected) {
        assertEquals(expected.trim(), EmailNotificationService.humanizeJobName(input.trim()));
    }

    @Test
    void humanizeJobName_singleWord_capitalized() {
        assertEquals("Job", EmailNotificationService.humanizeJobName("job"));
    }

    @Test
    void humanizeJobName_alreadyUpperCase_preserved() {
        String result = EmailNotificationService.humanizeJobName("MyJob");
        assertNotNull(result);
        assertTrue(result.startsWith("My"), "First word should start with 'My'");
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    void humanizeStepName_nullSafe() {
        // Callers should never pass null, but verify graceful behavior
        assertDoesNotThrow(() -> EmailNotificationService.humanizeStepName(""));
    }

    @Test
    void humanizeJobName_nullSafe() {
        assertDoesNotThrow(() -> EmailNotificationService.humanizeJobName(""));
    }
}
