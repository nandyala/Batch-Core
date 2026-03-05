package com.example.batch.multithreaded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.time.LocalDateTime;

/**
 * Processes a {@link WorkItem} in a multi-threaded chunk step.
 *
 * <p><strong>Thread safety:</strong> This class is stateless — no instance fields
 * are written during processing. It is safe to share a single bean instance across
 * all concurrent chunk threads.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Perform the unit of work described by {@code payload}.</li>
 *   <li>Set {@code status = PROCESSED} and {@code processedAt = now}.</li>
 *   <li>Throw {@link IllegalStateException} for logically invalid items
 *       (PERMANENT skip — no retry).</li>
 *   <li>Throw {@link java.sql.SQLTransientException} for transient DB errors
 *       (TRANSIENT — retried up to retry-limit before skip).</li>
 * </ol>
 *
 * <p>Returns {@code null} to filter items that should be skipped silently
 * (increments Spring Batch filter count).
 */
public class WorkItemProcessor implements ItemProcessor<WorkItem, WorkItem> {

    private static final Logger log = LoggerFactory.getLogger(WorkItemProcessor.class);

    @Override
    public WorkItem process(WorkItem item) {
        // Validate payload is not empty
        if (item.getPayload() == null || item.getPayload().isBlank()) {
            throw new IllegalArgumentException(
                "WorkItem id=" + item.getId() + " has an empty payload — skipping (PERMANENT)");
        }

        log.debug("[thread={}] Processing WorkItem id={} priority={}",
                  Thread.currentThread().getName(), item.getId(), item.getPriority());

        // -----------------------------------------------------------------
        // >>> Replace this block with real business logic <<<
        // -----------------------------------------------------------------
        String processedPayload = item.getPayload().trim().toUpperCase();
        item.setPayload(processedPayload);
        // -----------------------------------------------------------------

        item.setStatus     ("PROCESSED");
        item.setProcessedAt(LocalDateTime.now());

        return item;
    }
}
