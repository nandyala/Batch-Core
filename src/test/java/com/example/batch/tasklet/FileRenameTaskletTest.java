package com.example.batch.tasklet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.repeat.RepeatStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FileRenameTasklet}.
 *
 * <p>Uses JUnit 5's {@link TempDir} to create an isolated temporary directory
 * per test. No Spring context or Mockito required — {@link FileRenameTasklet}
 * does not use the {@code StepContribution} or {@code ChunkContext} arguments,
 * so both are passed as {@code null}.
 */
class FileRenameTaskletTest {

    @TempDir
    Path tempDir;

    private FileRenameTasklet tasklet;
    private static final String BATCH_DATE = "2026-01-15";

    @BeforeEach
    void setUp() {
        tasklet = new FileRenameTasklet();
        tasklet.setOutputDir(tempDir.toString());
        tasklet.setBatchDate(BATCH_DATE);
    }

    // ---------------------------------------------------------------
    // Happy path: file is renamed to .csv
    // ---------------------------------------------------------------

    @Test
    void execute_tmpFileExists_renamedToCsv() throws Exception {
        Path tmpFile = writeTmpFile("data,row1\ndata,row2\n");

        RepeatStatus status = tasklet.execute(null, null);

        assertEquals(RepeatStatus.FINISHED, status, "execute() must return FINISHED");

        // .tmp file should be gone, .csv should exist
        assertFalse(Files.exists(tmpFile), ".tmp file should no longer exist after rename");

        Path csvFile = expectedCsvPath();
        assertTrue(Files.exists(csvFile), ".csv file should exist after rename");
        assertEquals("data,row1\ndata,row2\n", Files.readString(csvFile),
                "CSV content must match original tmp file content");
    }

    @Test
    void execute_existingCsvFile_overwritten() throws Exception {
        // Pre-existing .csv from a previous run should be replaced
        Files.writeString(expectedCsvPath(), "old content");
        writeTmpFile("new content");

        tasklet.execute(null, null);

        String content = Files.readString(expectedCsvPath());
        assertEquals("new content", content,
                "Pre-existing .csv must be overwritten by the new file");
    }

    @Test
    void execute_emptyTmpFile_renamedSuccessfully() throws Exception {
        writeTmpFile("");  // empty file

        assertDoesNotThrow(() -> tasklet.execute(null, null));

        assertTrue(Files.exists(expectedCsvPath()), ".csv should exist even for empty tmp file");
        assertEquals(0, Files.size(expectedCsvPath()), "Empty source → empty target");
    }

    // ---------------------------------------------------------------
    // Missing source file → IllegalStateException
    // ---------------------------------------------------------------

    @Test
    void execute_tmpFileMissing_throwsIllegalStateException() {
        // Do NOT create the .tmp file

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> tasklet.execute(null, null));

        assertTrue(ex.getMessage().contains(".csv.tmp") || ex.getMessage().contains("tmp"),
                "Exception should mention the missing tmp file: " + ex.getMessage());
    }

    @Test
    void execute_missingTmpFile_exceptionMessageContainsAbsolutePath() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> tasklet.execute(null, null));

        // The message should contain an absolute path for easy debugging
        assertTrue(ex.getMessage().contains(tempDir.toString()),
                "Exception message should contain the output directory path: " + ex.getMessage());
    }

    // ---------------------------------------------------------------
    // File naming: batchDate is embedded in filenames
    // ---------------------------------------------------------------

    @Test
    void execute_fileNamesContainBatchDate() throws Exception {
        writeTmpFile("content");
        tasklet.execute(null, null);

        // CSV file name should contain the batchDate
        assertTrue(Files.exists(expectedCsvPath()),
                "CSV file should exist at: " + expectedCsvPath());
        assertTrue(expectedCsvPath().getFileName().toString().contains(BATCH_DATE),
                "CSV filename should contain batchDate: " + BATCH_DATE);
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Creates the .tmp file with the given content and returns its Path. */
    private Path writeTmpFile(String content) throws IOException {
        Path tmp = tempDir.resolve("mt-customer-report-" + BATCH_DATE + ".csv.tmp");
        Files.writeString(tmp, content);
        return tmp;
    }

    /** Returns the expected .csv target path. */
    private Path expectedCsvPath() {
        return tempDir.resolve("mt-customer-report-" + BATCH_DATE + ".csv");
    }
}
