package com.example.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * Lightweight job-boundary logger.
 *
 * <p>Logs job name and status at start and end.
 * Full statistics aggregation and email are handled by
 * {@link com.example.batch.tasklet.StatisticsAndEmailTasklet}
 * as the last step of each job.
 *
 * <p>No Spring annotations — wired via XML in {@code batch-listeners.xml}.
 */
public class JobStatisticsListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobStatisticsListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("======================================================");
        log.info("  JOB STARTED");
        log.info("  Name         : {}", jobExecution.getJobInstance().getJobName());
        log.info("  Execution ID : {}", jobExecution.getId());
        log.info("  Parameters   : {}", jobExecution.getJobParameters());
        log.info("======================================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("======================================================");
        log.info("  JOB FINISHED");
        log.info("  Name         : {}", jobExecution.getJobInstance().getJobName());
        log.info("  Execution ID : {}", jobExecution.getId());
        log.info("  Status       : {}", jobExecution.getStatus());
        log.info("  Exit Code    : {}", jobExecution.getExitStatus().getExitCode());
        log.info("======================================================");
    }
}
