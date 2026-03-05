package com.example.batch.service;

import com.example.batch.model.JobStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.text.SimpleDateFormat;

/**
 * Sends a business-friendly HTML job report email via Spring's {@link JavaMailSender}.
 *
 * <p>Language is tailored for business users — technical batch terms like "read/written/skipped"
 * are replaced with "Received / Saved / Flagged for Review / Errors". Step names are
 * automatically humanized (e.g. {@code processInvoicesStep} → "Process Invoices").
 *
 * <p>No Spring annotations — wired via XML in {@code batch-mail.xml}.
 */
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private static final SimpleDateFormat DISPLAY_FMT      = new SimpleDateFormat("dd MMM yyyy, HH:mm");
    private static final SimpleDateFormat SUBJECT_DATE_FMT = new SimpleDateFormat("dd MMM yyyy HH:mm");

    /** Injected via XML. */
    private JavaMailSender mailSender;

    /** From address — injected via XML. */
    private String fromAddress;

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public void sendJobReport(JobStatistics stats, String toAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(buildSubject(stats));
            helper.setText(buildHtmlBody(stats), true);

            mailSender.send(message);
            log.debug("Email sent to {} for job '{}'", toAddress, stats.getJobName());

        } catch (MessagingException ex) {
            throw new RuntimeException(
                "Failed to send email report for job: " + stats.getJobName(), ex);
        }
    }

    // ---------------------------------------------------------------
    // Subject
    // ---------------------------------------------------------------

    private String buildSubject(JobStatistics stats) {
        boolean completed = "COMPLETED".equals(stats.getComputedStatus());
        String statusLabel = completed ? "Completed Successfully" : "Completed with Errors";
        String dateStr = stats.getEndTime() != null ? SUBJECT_DATE_FMT.format(stats.getEndTime()) : "";
        return String.format("%s — %s | %d Saved · %d Flagged · %d Errors | %s",
                humanizeJobName(stats.getJobName()),
                statusLabel,
                stats.getTotalWriteCount(),
                stats.getTotalFilterCount(),
                stats.getTotalSkipCount(),
                dateStr);
    }

    // ---------------------------------------------------------------
    // HTML body
    // ---------------------------------------------------------------

    private String buildHtmlBody(JobStatistics stats) {
        boolean completed  = "COMPLETED".equals(stats.getComputedStatus());
        String statusColor = completed ? "#27ae60" : "#e74c3c";
        String statusLabel = completed ? "&#10003;&nbsp; Completed Successfully"
                                       : "&#10007;&nbsp; Completed with Errors";
        String reportTime  = stats.getEndTime()   != null ? DISPLAY_FMT.format(stats.getEndTime())   : "N/A";
        String startTime   = stats.getStartTime() != null ? DISPLAY_FMT.format(stats.getStartTime()) : "N/A";

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>")
          .append("<body style='margin:0;padding:0;background:#f0f2f5;")
          .append("font-family:Arial,Helvetica,sans-serif;color:#2c3e50;'>")

          // ── Header banner ────────────────────────────────────────────
          .append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='background:#2c3e50;'>")
          .append("<tr>")
          .append("<td style='padding:28px 36px;'>")
          .append("<div style='color:#fff;font-size:20px;font-weight:bold;'>")
          .append("Job Completion Report</div>")
          .append("<div style='color:#bdc3c7;font-size:13px;margin-top:6px;'>")
          .append(humanizeJobName(stats.getJobName()))
          .append(" &nbsp;&middot;&nbsp; ").append(reportTime)
          .append(" &nbsp;&middot;&nbsp; ID:&nbsp;<span style='font-family:monospace;'>")
          .append(escapeHtml(stats.getCorrelationId())).append("</span>")
          .append("</div>")
          .append("</td>")
          .append("<td style='padding:28px 36px;text-align:right;vertical-align:middle;'>")
          .append("<span style='background:").append(statusColor)
          .append(";color:#fff;padding:10px 22px;border-radius:20px;")
          .append("font-size:13px;font-weight:bold;white-space:nowrap;'>")
          .append(statusLabel).append("</span>")
          .append("</td></tr></table>")

          // ── KPI summary cards ────────────────────────────────────────
          .append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='padding:24px 36px 12px;'>")
          .append("<tr>")
          .append(kpiCard("#3498db", stats.getTotalReadCount(),   "Records Received",    false))
          .append(kpiCard("#27ae60", stats.getTotalWriteCount(),  "Successfully Saved",  false))
          .append(kpiCard("#f39c12", stats.getTotalFilterCount(), "Flagged for Review",  false))
          .append(kpiCard(stats.getTotalSkipCount() > 0 ? "#e74c3c" : "#95a5a6",
                          stats.getTotalSkipCount(), "Errors", true))
          .append("</tr></table>")

          // ── Step breakdown ───────────────────────────────────────────
          .append(buildStepTable(stats))

          // ── Top Issues (only if errors occurred) ─────────────────────
          .append(buildTopIssuesSection(stats))

          // ── Run details ──────────────────────────────────────────────
          .append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='padding:0 36px 24px;'>")
          .append("<tr><td style='background:#fff;border-radius:8px;padding:18px 22px;'>")
          .append("<div style='font-size:13px;font-weight:bold;color:#7f8c8d;")
          .append("text-transform:uppercase;letter-spacing:1px;margin-bottom:12px;'>")
          .append("Run Details</div>")
          .append("<table cellpadding='5' cellspacing='0'>")
          .append(infoRow("Started",        startTime))
          .append(infoRow("Finished",       reportTime))
          .append(infoRow("Duration",       stats.getFormattedDuration()))
          .append(infoRow("Correlation ID", "<span style='font-family:monospace;'>"
                          + escapeHtml(stats.getCorrelationId()) + "</span>"))
          .append(infoRow("Config Hash",    "<span style='font-family:monospace;'>"
                          + escapeHtml(stats.getConfigHash()) + "</span>"))
          .append("</table>")
          .append("</td></tr></table>")

          // ── Footer ───────────────────────────────────────────────────
          .append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='padding:0 36px 36px;'>")
          .append("<tr><td style='color:#bdc3c7;font-size:11px;'>")
          .append("Execution ID: ").append(stats.getJobExecutionId())
          .append(" &nbsp;&middot;&nbsp; Generated by Spring Batch Framework")
          .append("</td></tr></table>")

          .append("</body></html>");

        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Step breakdown table
    // ---------------------------------------------------------------

    private String buildStepTable(JobStatistics stats) {
        if (stats.getStepStats().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='padding:0 36px 16px;'>")
          .append("<tr><td style='background:#fff;border-radius:8px;padding:20px 22px;'>")
          .append("<div style='font-size:14px;font-weight:bold;color:#2c3e50;margin-bottom:14px;'>")
          .append("Step Breakdown</div>")
          .append("<table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse;'>")

          // Column headers
          .append("<tr style='border-bottom:2px solid #ecf0f1;'>")
          .append(th("Step",             "left"))
          .append(th("Received",         "right"))
          .append(th("Saved",            "right"))
          .append(th("Flagged",          "right"))
          .append(th("Errors",           "right"))
          .append(th("Result",           "center"))
          .append("</tr>");

        boolean alt = false;
        for (JobStatistics.StepStats s : stats.getStepStats()) {
            boolean failed  = "FAILED".equals(s.getExitStatus().getExitCode());
            String rowBg    = failed ? "#fef5f5" : (alt ? "#f9fafb" : "#fff");
            String badgeBg  = failed ? "#e74c3c" : "#27ae60";
            String badgeTxt = failed ? "Failed"  : "Completed";
            String errColor = s.getSkipCount() > 0 ? "#e74c3c" : "#95a5a6";

            sb.append("<tr style='background:").append(rowBg)
              .append(";border-bottom:1px solid #ecf0f1;'>")
              .append("<td style='padding:11px 8px;font-size:13px;'>")
              .append(humanizeStepName(s.getStepName())).append("</td>")
              .append(numCell(s.getReadCount(),   "#2c3e50"))
              .append(numCell(s.getWriteCount(),  "#27ae60"))
              .append(numCell(s.getFilterCount(), "#f39c12"))
              .append(numCell(s.getSkipCount(),   errColor))
              .append("<td style='text-align:center;padding:11px 8px;'>")
              .append("<span style='background:").append(badgeBg)
              .append(";color:#fff;padding:3px 12px;border-radius:10px;font-size:11px;'>")
              .append(badgeTxt).append("</span></td>")
              .append("</tr>");
            alt = !alt;
        }
        sb.append("</table></td></tr></table>");
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Top issues section
    // ---------------------------------------------------------------

    private String buildTopIssuesSection(JobStatistics stats) {
        if (stats.getErrors().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<table width='100%' cellpadding='0' cellspacing='0'")
          .append(" style='padding:0 36px 16px;'>")
          .append("<tr><td style='background:#fff;border-radius:8px;")
          .append("border-left:4px solid #e74c3c;padding:18px 22px;'>")
          .append("<div style='font-size:14px;font-weight:bold;color:#e74c3c;margin-bottom:12px;'>")
          .append("&#9888;&nbsp; Top Issues</div>")
          .append("<table width='100%' cellpadding='0' cellspacing='0'>");

        for (String err : stats.getErrors()) {
            sb.append("<tr><td style='padding:4px 0;font-size:12px;")
              .append("color:#555;border-bottom:1px solid #f5f5f5;'>")
              .append("&bull;&nbsp;").append(escapeHtml(err))
              .append("</td></tr>");
        }

        sb.append("</table></td></tr></table>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ---------------------------------------------------------------
    // Name humanization helpers
    // ---------------------------------------------------------------

    /**
     * Converts a camelCase step bean name to a readable title.
     * {@code processInvoicesStep} → {@code "Process Invoices"}
     * {@code readAndProcessStep}  → {@code "Read And Process"}
     */
    public static String humanizeStepName(String stepName) {
        // Strip trailing "Step" suffix (case-insensitive)
        String name = stepName.replaceAll("(?i)Step$", "").trim();
        // Split camelCase
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2")
                            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return toTitleCase(spaced);
    }

    /**
     * Converts a camelCase job bean name to a readable title.
     * {@code invoiceJob} → {@code "Invoice Job"}
     */
    public static String humanizeJobName(String jobName) {
        String spaced = jobName.replaceAll("([a-z])([A-Z])", "$1 $2")
                               .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2");
        return toTitleCase(spaced);
    }

    private static String toTitleCase(String input) {
        String[] words = input.split("[\\s_\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // HTML component helpers
    // ---------------------------------------------------------------

    private String kpiCard(String color, long value, String label, boolean lastCard) {
        String padding = lastCard ? "0 0 0 8px" : "0 8px 0 0";
        return "<td style='padding:" + padding + ";width:25%;vertical-align:top;'>"
             + "<div style='background:#fff;border-radius:8px;padding:22px 16px;"
             + "text-align:center;border-top:4px solid " + color + ";'>"
             + "<div style='font-size:34px;font-weight:bold;color:" + color
             + ";line-height:1;'>" + value + "</div>"
             + "<div style='color:#7f8c8d;font-size:12px;margin-top:8px;'>"
             + label + "</div>"
             + "</div></td>";
    }

    private String th(String label, String align) {
        return "<th style='text-align:" + align + ";color:#7f8c8d;"
             + "font-size:11px;text-transform:uppercase;letter-spacing:0.5px;"
             + "padding:6px 8px;font-weight:normal;'>"
             + label + "</th>";
    }

    private String numCell(long value, String color) {
        return "<td style='text-align:right;padding:11px 8px;font-size:13px;"
             + "font-weight:bold;color:" + color + ";'>" + value + "</td>";
    }

    private String infoRow(String label, String value) {
        return "<tr>"
             + "<td style='color:#7f8c8d;font-size:13px;padding-right:16px;white-space:nowrap;'>"
             + label + "</td>"
             + "<td style='font-size:13px;font-weight:bold;'>" + value + "</td>"
             + "</tr>";
    }

    // ---------------------------------------------------------------
    // Setters — called by Spring XML <property> injection
    // ---------------------------------------------------------------

    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }
}
