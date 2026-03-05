package com.example.batch.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BatchJobParameterValidator}.
 *
 * <p>No Spring context needed — pure Java unit test.
 */
class BatchJobParameterValidatorTest {

    private BatchJobParameterValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BatchJobParameterValidator();
    }

    // ---------------------------------------------------------------
    // Required parameter checks
    // ---------------------------------------------------------------

    @Test
    void validate_noRequiredParams_emptyParameters_passes() {
        // Default: no required params configured
        JobParameters params = new JobParametersBuilder().toJobParameters();
        assertDoesNotThrow(() -> validator.validate(params));
    }

    @Test
    void validate_requiredParamPresent_passes() throws InvalidJobParametersException {
        validator.setRequiredParams(Set.of("batchDate"));
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", "2026-01-15")
                .toJobParameters();
        validator.validate(params);  // must not throw
    }

    @Test
    void validate_requiredParamMissing_throwsWithHelpfulMessage() {
        validator.setRequiredParams(Set.of("batchDate"));
        JobParameters params = new JobParametersBuilder().toJobParameters();

        InvalidJobParametersException ex = assertThrows(
                InvalidJobParametersException.class,
                () -> validator.validate(params));

        assertTrue(ex.getMessage().contains("batchDate"),
                "Exception message should name the missing parameter");
        assertTrue(ex.getMessage().contains("Missing required"),
                "Exception message should say 'Missing required'");
    }

    @Test
    void validate_multipleRequiredParams_oneAbsent_throws() {
        validator.setRequiredParams(Set.of("batchDate", "region"));
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", "2026-01-15")
                // "region" is missing
                .toJobParameters();

        assertThrows(InvalidJobParametersException.class,
                () -> validator.validate(params));
    }

    @Test
    void validate_multipleRequiredParams_allPresent_passes() throws InvalidJobParametersException {
        validator.setRequiredParams(Set.of("batchDate", "region"));
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", "2026-01-15")
                .addString("region", "EMEA")
                .toJobParameters();
        validator.validate(params);  // must not throw
    }

    // ---------------------------------------------------------------
    // batchDate format validation
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "valid batchDate=\"{0}\" passes")
    @ValueSource(strings = {"2026-01-15", "2025-12-31", "2024-02-29", "2000-01-01"})
    void validate_validBatchDate_passes(String date) throws InvalidJobParametersException {
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", date)
                .toJobParameters();
        validator.validate(params);
    }

    @ParameterizedTest(name = "invalid batchDate=\"{0}\" throws")
    @ValueSource(strings = {
        "15-01-2026",    // wrong order
        "2026/01/15",    // wrong separator
        "20260115",      // no separators
        "2026-1-5",      // no zero-padding
        "not-a-date",    // nonsense
        ""               // empty string
    })
    void validate_invalidBatchDate_throws(String date) {
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", date)
                .toJobParameters();

        InvalidJobParametersException ex = assertThrows(
                InvalidJobParametersException.class,
                () -> validator.validate(params));

        assertTrue(ex.getMessage().contains("batchDate"),
                "Exception should mention batchDate");
        assertTrue(ex.getMessage().contains("yyyy-MM-dd"),
                "Exception should describe the expected format");
    }

    @Test
    void validate_batchDateAbsent_passes() throws InvalidJobParametersException {
        // batchDate is optional — absence is valid unless marked required
        JobParameters params = new JobParametersBuilder()
                .addString("otherParam", "value")
                .toJobParameters();
        validator.validate(params);  // must not throw
    }

    @Test
    void validate_batchDateValidAndRequiredPresent_passes() throws InvalidJobParametersException {
        validator.setRequiredParams(Set.of("batchDate"));
        JobParameters params = new JobParametersBuilder()
                .addString("batchDate", "2026-06-30")
                .toJobParameters();
        validator.validate(params);  // both checks must pass
    }
}
