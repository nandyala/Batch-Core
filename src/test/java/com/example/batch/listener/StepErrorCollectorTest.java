package com.example.batch.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import org.springframework.dao.TransientDataAccessResourceException;

import java.sql.SQLTransientException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StepErrorCollector}.
 *
 * <p>Uses real Spring Batch domain objects ({@link StepExecution}, {@link JobExecution})
 * constructed from their public constructors — no Spring context required.
 */
class StepErrorCollectorTest {

    private StepErrorCollector collector;
    private StepExecution stepExecution;

    @BeforeEach
    void setUp() {
        collector = new StepErrorCollector();

        JobInstance jobInstance = new JobInstance(1L, "testJob");
        JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        stepExecution = new StepExecution("testStep", jobExecution);

        // Wire the step execution into the collector (mimics beforeStep callback)
        collector.beforeStep(stepExecution);
    }

    // ---------------------------------------------------------------
    // afterStep: must not override step exit status
    // ---------------------------------------------------------------

    @Test
    void afterStep_returnsNull_doesNotOverrideExitStatus() {
        assertNull(collector.afterStep(stepExecution),
                "afterStep must return null so the step's own exit status is preserved");
    }

    // ---------------------------------------------------------------
    // Skip event recording
    // ---------------------------------------------------------------

    @Test
    void onSkipInRead_recordsError() {
        collector.onSkipInRead(new IllegalArgumentException("bad data on line 5"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Read skip"),   "Should label as Read skip");
        assertTrue(errors.get(0).contains("PERMANENT"),   "IAE is a permanent error");
        assertTrue(errors.get(0).contains("bad data"),    "Should include exception message");
    }

    @Test
    void onSkipInProcess_recordsError() {
        collector.onSkipInProcess(new Object(), new IllegalStateException("processor failed"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Process skip"), "Should label as Process skip");
        assertTrue(errors.get(0).contains("PERMANENT"),    "ISE is a permanent error");
    }

    @Test
    void onSkipInWrite_recordsError() {
        collector.onSkipInWrite(new Object(), new RuntimeException("write failed"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("Write skip"), "Should label as Write skip");
    }

    // ---------------------------------------------------------------
    // Transient vs Permanent classification
    // ---------------------------------------------------------------

    @Test
    void onSkipInRead_sqlTransientException_classifiedAsTransient() {
        collector.onSkipInRead(new SQLTransientException("connection reset"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("TRANSIENT"), "SQLTransientException should be TRANSIENT");
    }

    @Test
    void onSkipInRead_regularException_classifiedAsPermanent() {
        collector.onSkipInRead(new RuntimeException("unrecoverable"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("PERMANENT"), "RuntimeException should be PERMANENT");
    }

    // ---------------------------------------------------------------
    // MAX_ERRORS cap
    // ---------------------------------------------------------------

    @Test
    void skipEvents_cappedAtMaxErrors_plusOverflowMessage() {
        // Add MAX_ERRORS + 5 errors
        int overLimit = StepErrorCollector.MAX_ERRORS + 5;
        for (int i = 0; i < overLimit; i++) {
            collector.onSkipInRead(new RuntimeException("error " + i));
        }

        List<String> errors = getErrors();

        // List should be MAX_ERRORS + 1 (the overflow message occupies one slot)
        assertEquals(StepErrorCollector.MAX_ERRORS + 1, errors.size(),
                "Should store MAX_ERRORS entries plus one overflow/suppressed message");

        // Last entry should mention "suppressed"
        String last = errors.get(errors.size() - 1);
        assertTrue(last.contains("suppressed") || last.contains("further errors"),
                "Last entry should indicate further errors were suppressed: " + last);
    }

    @Test
    void skipEvents_exactlyAtMaxErrors_allStored() {
        for (int i = 0; i < StepErrorCollector.MAX_ERRORS; i++) {
            collector.onSkipInRead(new RuntimeException("error " + i));
        }

        List<String> errors = getErrors();
        assertEquals(StepErrorCollector.MAX_ERRORS, errors.size(),
                "Exactly MAX_ERRORS entries should all be stored");
    }

    // ---------------------------------------------------------------
    // beforeStep not called (null stepExecution) — no NPE
    // ---------------------------------------------------------------

    @Test
    void skipBeforeBeforeStep_noNullPointerException() {
        StepErrorCollector freshCollector = new StepErrorCollector();
        // Never called beforeStep → stepExecution is null

        assertDoesNotThrow(() -> freshCollector.onSkipInRead(new RuntimeException("oops")),
                "Should not throw NPE when stepExecution is null");
    }

    // ---------------------------------------------------------------
    // Multiple steps: errors are stored per-step in ExecutionContext
    // ---------------------------------------------------------------

    @Test
    void errors_storedInStepExecutionContext_underCorrectKey() {
        collector.onSkipInRead(new RuntimeException("ctx error"));

        Object stored = stepExecution.getExecutionContext().get(StepErrorCollector.ERRORS_KEY);
        assertNotNull(stored, "Errors should be stored in StepExecution.ExecutionContext");
        assertInstanceOf(List.class, stored);
    }

    // ---------------------------------------------------------------
    // classify() — TransientDataAccessException branch
    // ---------------------------------------------------------------

    @Test
    void onSkipInRead_springTransientException_classifiedAsTransient() {
        // TransientDataAccessResourceException extends TransientDataAccessException (not SQLTransientException)
        // → exercises the second condition in classify()'s OR expression
        collector.onSkipInRead(new TransientDataAccessResourceException("connection timeout"));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("TRANSIENT"),
                "Spring TransientDataAccessException should be classified as TRANSIENT");
    }

    // ---------------------------------------------------------------
    // summarize() — null / blank message and >150-char truncation
    // ---------------------------------------------------------------

    @Test
    void onSkipInRead_exceptionWithNullMessage_showsNoMessagePlaceholder() {
        // RuntimeException(String) with null → getMessage() returns null
        collector.onSkipInRead(new RuntimeException((String) null));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("(no message)"),
                "Null exception message should produce '(no message)' in the error entry");
    }

    @Test
    void onSkipInRead_exceptionWithBlankMessage_showsNoMessagePlaceholder() {
        // getMessage() returns a whitespace-only string → isBlank() = true
        collector.onSkipInRead(new RuntimeException("   "));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("(no message)"),
                "Blank exception message should produce '(no message)' in the error entry");
    }

    @Test
    void onSkipInRead_exceptionWithLongMessage_truncatedAt150Chars() {
        // Message longer than 150 chars → summarize() truncates with "…"
        String longMsg = "X".repeat(200);
        collector.onSkipInRead(new RuntimeException(longMsg));

        List<String> errors = getErrors();
        assertEquals(1, errors.size());
        String entry = errors.get(0);
        assertTrue(entry.contains("…"),
                "Long exception message (>150 chars) should be truncated with '…': " + entry);
        // The entry format is "Read skip — PERMANENT: RuntimeException — <message>"
        // Use lastIndexOf to skip past the first " — " (between skip type and classification)
        // and reach the " — " that separates the class name from the actual message text.
        String messagePart = entry.substring(entry.lastIndexOf(" — ") + 3);
        assertTrue(messagePart.length() <= 154,   // 150 chars + "…" (multi-byte) + small margin
                "Truncated message portion must not exceed 150 characters: length=" + messagePart.length());
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private List<String> getErrors() {
        return (List<String>) stepExecution.getExecutionContext()
                .get(StepErrorCollector.ERRORS_KEY);
    }
}
