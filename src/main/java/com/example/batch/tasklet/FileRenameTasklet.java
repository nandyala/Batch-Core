package com.example.batch.tasklet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Renames the {@code .csv.tmp} file produced by {@code mt-db-to-file-job}
 * to its final {@code .csv} name once all chunk writing has completed.
 *
 * <h3>Why write to .tmp first?</h3>
 * {@link org.springframework.batch.item.file.FlatFileItemWriter} writes
 * incrementally — a downstream consumer polling the output directory would see
 * a partial file mid-run. Writing to {@code .tmp} first and atomically renaming
 * at the end ensures the final file appears only when it is complete.
 *
 * <h3>Restart safety</h3>
 * If the chunk step fails, the {@code .tmp} remains on disk.  On restart, the
 * writer uses {@code shouldDeleteIfExists=true} so the partial file is
 * overwritten before this tasklet runs.
 *
 * <h3>Configured by mt-db-to-file-job.xml</h3>
 * <pre>{@code
 * <bean id="renameFileTasklet"
 *       class="com.example.batch.tasklet.FileRenameTasklet"
 *       scope="step">
 *     <property name="outputDir" value="${batch.dbtofile.outputDir}"/>
 *     <property name="batchDate" value="#{jobParameters['batchDate']}"/>
 * </bean>
 * }</pre>
 */
public class FileRenameTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(FileRenameTasklet.class);

    private String outputDir;
    private String batchDate;

    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }
    public void setBatchDate(String batchDate)  { this.batchDate = batchDate; }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws IOException {

        Path source = Path.of(outputDir, "mt-customer-report-" + batchDate + ".csv.tmp");
        Path target = Path.of(outputDir, "mt-customer-report-" + batchDate + ".csv");

        if (!Files.exists(source)) {
            throw new IllegalStateException(
                "Expected tmp file not found — did the chunk step complete successfully? Path: "
                + source.toAbsolutePath());
        }

        log.info("Renaming output file: {} → {}", source.getFileName(), target.getFileName());

        try {
            // Atomic move: the final file appears all-at-once with no partial-visibility window
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("Atomic rename succeeded → {}", target.toAbsolutePath());
        } catch (AtomicMoveNotSupportedException ex) {
            // Cross-mount or filesystem limitation — fall back to non-atomic replace.
            // Still safe: consumers only look for the .csv extension, not .csv.tmp.
            log.warn("Atomic rename not supported on this filesystem — falling back to REPLACE_EXISTING move");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Non-atomic rename succeeded → {}", target.toAbsolutePath());
        }

        return RepeatStatus.FINISHED;
    }
}
