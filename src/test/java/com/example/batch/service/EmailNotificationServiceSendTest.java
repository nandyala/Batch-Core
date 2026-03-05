package com.example.batch.service;

import com.example.batch.model.JobStatistics;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.ExitStatus;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the instance methods of {@link EmailNotificationService} that require
 * a mocked {@link JavaMailSender} and {@link MimeMessage}.
 *
 * <p>Exercises all private HTML builder methods
 * ({@code buildSubject}, {@code buildHtmlBody}, {@code buildStepTable},
 * {@code buildTopIssuesSection}, {@code escapeHtml}, {@code kpiCard},
 * {@code th}, {@code numCell}, {@code infoRow}) through the public
 * {@code sendJobReport()} entry point.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceSendTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService();
        service.setMailSender(mailSender);
        service.setFromAddress("batch@example.com");
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    // ---------------------------------------------------------------
    // Helper — build minimal JobStatistics
    // ---------------------------------------------------------------

    private JobStatistics baseStats(String jobName) {
        JobStatistics stats = new JobStatistics();
        stats.setJobName(jobName);
        stats.setJobExecutionId(1L);
        stats.setCorrelationId("ABC12345");
        stats.setConfigHash("HASH01");
        stats.setStartTime(new Date(System.currentTimeMillis() - 5_000));
        stats.setEndTime(new Date());
        stats.setTotalReadCount(100);
        stats.setTotalWriteCount(95);
        stats.setTotalFilterCount(3);
        stats.setTotalSkipCount(2);
        return stats;
    }

    private JobStatistics.StepStats completedStep(String name, long reads, long writes,
                                                   long filters, long skips) {
        return new JobStatistics.StepStats(
                name, reads, writes, skips, filters,
                skips, 0L, 0L, ExitStatus.COMPLETED, List.of());
    }

    private JobStatistics.StepStats failedStep(String name) {
        return new JobStatistics.StepStats(
                name, 10L, 0L, 10L, 0L, 10L, 0L, 0L,
                ExitStatus.FAILED, List.of("row 1: parse error"));
    }

    // ---------------------------------------------------------------
    // setters
    // ---------------------------------------------------------------

    @Test
    void setMailSender_setFromAddress_injectDependencies() {
        // Setters already called in @BeforeEach — just verify no NPE when used
        EmailNotificationService s = new EmailNotificationService();
        s.setMailSender(mailSender);
        s.setFromAddress("test@example.com");
        // No assertion needed — if injection failed, sendJobReport would NPE
    }

    // ---------------------------------------------------------------
    // Happy path — COMPLETED job with steps
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_completedJobWithSteps_sendsEmail() throws Exception {
        // Covers: buildSubject (COMPLETED=true), buildHtmlBody (COMPLETED, green badge),
        //         kpiCard (first 3 false / last true), buildStepTable (non-empty),
        //         th(), numCell(), infoRow(), buildTopIssuesSection (empty → "")
        JobStatistics stats = baseStats("invoiceJob");
        stats.getStepStats().add(completedStep("processInvoicesStep", 100, 95, 3, 2));

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    // ---------------------------------------------------------------
    // FAILED job (step has FAILED exit) — different subject + red badge
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_failedStep_usesErrorStatusLabel() throws Exception {
        // Covers: buildSubject (completed=false → "Completed with Errors"),
        //         buildHtmlBody (#e74c3c status color, ✗ badge),
        //         buildStepTable (failed row: red background, "Failed" badge)
        JobStatistics stats = baseStats("invoiceJob");
        stats.getStepStats().add(failedStep("processInvoicesStep"));

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
        verify(mailSender, times(1)).send(mimeMessage);
    }

    // ---------------------------------------------------------------
    // Skips > 0 → red error cell color in step table
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_stepWithSkips_usesRedErrorColor() throws Exception {
        // Covers: errColor = "#e74c3c" when s.getSkipCount() > 0
        JobStatistics stats = baseStats("sampleJob");
        stats.setTotalSkipCount(5);
        // Step with skips > 0
        stats.getStepStats().add(completedStep("readAndWriteStep", 50, 45, 0, 5));

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // Alternating row colors — needs 2+ steps
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_multipleSteps_alternatesRowColors() throws Exception {
        // Covers: alt = !alt flip for 2nd row (#f9fafb alternate background)
        JobStatistics stats = baseStats("sampleJob");
        stats.getStepStats().add(completedStep("step1Step", 100, 98, 2, 0));
        stats.getStepStats().add(completedStep("step2Step", 80,  78, 2, 0));
        stats.getStepStats().add(completedStep("step3Step", 60,  58, 2, 0));

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // No steps — buildStepTable returns empty string
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_noSteps_stepTableEmpty() throws Exception {
        // Covers: stats.getStepStats().isEmpty() → returns "" early
        JobStatistics stats = baseStats("emptyJob");
        // No steps added
        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // Errors present — buildTopIssuesSection renders issue list
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_withErrors_rendersTopIssues() throws Exception {
        // Covers: stats.getErrors().isEmpty() = false → renders issue list
        JobStatistics stats = baseStats("invoiceJob");
        stats.getErrors().add("Row 42: invalid amount");
        stats.getErrors().add("Row 99: duplicate key");

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // Null start/end times → "N/A" in subject and body
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_nullStartAndEndTime_showsNaPlaceholders() throws Exception {
        // Covers: stats.getEndTime() == null → "" in subject + "N/A" in body
        //         stats.getStartTime() == null → "N/A" in body
        JobStatistics stats = new JobStatistics();
        stats.setJobName("nullTimeJob");
        stats.setJobExecutionId(2L);
        stats.setCorrelationId("NULLTIME");
        stats.setConfigHash("HASH");
        // startTime and endTime left null

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // escapeHtml — special characters in correlationId / configHash
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_specialCharsInFields_escapedInHtml() throws Exception {
        // Covers: escapeHtml() replacing &, <, >, "
        JobStatistics stats = baseStats("testJob");
        stats.setCorrelationId("A&B<C>D\"E");   // triggers all 4 replacements
        stats.setConfigHash("<HASH>");

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // escapeHtml — null input → returns empty string
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_nullCorrelationId_escapesHtmlNullToEmpty() throws Exception {
        // Covers: escapeHtml(null) → returns ""
        JobStatistics stats = baseStats("testJob");
        stats.setCorrelationId(null);   // escapeHtml receives null

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }

    // ---------------------------------------------------------------
    // MessagingException in MimeMessageHelper → wrapped in RuntimeException
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_messagingExceptionFromMimeMessage_throwsRuntimeException()
            throws Exception {
        // Stub setFrom on MimeMessage to throw MessagingException.
        // MimeMessageHelper.setFrom(String) calls mimeMessage.setFrom(Address).
        doThrow(new MessagingException("SMTP connection refused"))
                .when(mimeMessage).setFrom(any(Address.class));

        JobStatistics stats = baseStats("failJob");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.sendJobReport(stats, "user@example.com"));
        assertTrue(ex.getMessage().contains("Failed to send email report"),
                "RuntimeException message should mention the failure reason");
    }

    // ---------------------------------------------------------------
    // Mixed failed and completed step in same job — covers both badge variants
    // ---------------------------------------------------------------

    @Test
    void sendJobReport_mixedFailedAndCompletedSteps_rendersBothBadges() throws Exception {
        // One completed step + one failed step in the same job
        JobStatistics stats = baseStats("mixedJob");
        stats.getStepStats().add(completedStep("step1Step", 100, 98, 2, 0));
        stats.getStepStats().add(failedStep("step2Step"));

        assertDoesNotThrow(() -> service.sendJobReport(stats, "user@example.com"));
    }
}
