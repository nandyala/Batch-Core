package com.example.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.listener.StepExecutionListener;

/**
 * Logs read / write / skip / filter counts after each step completes.
 *
 * <p>Attach to any chunk step via XML:
 * <pre>
 *   &lt;batch:tasklet ...&gt;
 *     &lt;batch:chunk .../&gt;
 *     &lt;batch:listeners&gt;
 *       &lt;batch:listener ref="stepStatisticsListener"/&gt;
 *     &lt;/batch:listeners&gt;
 *   &lt;/batch:tasklet&gt;
 * </pre>
 *
 * <p>No Spring annotations — wired via XML in {@code batch-listeners.xml}.
 */
public class StepStatisticsListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepStatisticsListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("--- Step starting: [{}] ---", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("--- Step completed: [{}] ---", stepExecution.getStepName());
        log.info("  Exit Status : {}", stepExecution.getExitStatus().getExitCode());
        log.info("  Read        : {}", stepExecution.getReadCount());
        log.info("  Written     : {}", stepExecution.getWriteCount());
        log.info("  Filtered    : {}", stepExecution.getFilterCount());
        log.info("  Skipped     : {}  (read={} | process={} | write={})",
                 stepExecution.getSkipCount(),
                 stepExecution.getReadSkipCount(),
                 stepExecution.getProcessSkipCount(),
                 stepExecution.getWriteSkipCount());
        log.info("  Commits     : {}", stepExecution.getCommitCount());
        log.info("  Rollbacks   : {}", stepExecution.getRollbackCount());
        return null; // do not override the step's own exit status
    }
}
