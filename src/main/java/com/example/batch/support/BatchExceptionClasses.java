package com.example.batch.support;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;

import java.sql.SQLTransientException;

/**
 * Canonical exception taxonomy for all Spring Batch jobs in this framework.
 *
 * <h3>Two categories</h3>
 * <dl>
 *   <dt>TRANSIENT (retryable + skippable)</dt>
 *   <dd>Temporary infrastructure failures — network blips, DB lock timeouts.
 *       Spring Batch will retry the item up to {@code retry-limit} times.
 *       If retries are exhausted the item counts as a skip.</dd>
 *
 *   <dt>PERMANENT (skippable only, no retry)</dt>
 *   <dd>Data or business-logic errors — bad input, constraint violations.
 *       Retrying the same item will always fail, so Spring Batch skips it
 *       immediately and moves to the next item.</dd>
 * </dl>
 *
 * <h3>How to reference in job XML</h3>
 * <pre>{@code
 * <batch:chunk ... skip-limit="10" retry-limit="3">
 *
 *   <!-- PERMANENT exceptions: skip immediately, no retry -->
 *   <batch:skippable-exception-classes>
 *     <batch:include class="java.lang.IllegalArgumentException"/>
 *     <batch:include class="java.lang.IllegalStateException"/>
 *     <batch:include class="org.springframework.dao.DataIntegrityViolationException"/>
 *     <!-- Also include transient ones so they can be skipped after retry exhaustion -->
 *     <batch:include class="java.sql.SQLTransientException"/>
 *     <batch:include class="org.springframework.dao.TransientDataAccessException"/>
 *   </batch:skippable-exception-classes>
 *
 *   <!-- TRANSIENT exceptions: retry up to retry-limit before skipping -->
 *   <batch:retryable-exception-classes>
 *     <batch:include class="java.sql.SQLTransientException"/>
 *     <batch:include class="org.springframework.dao.TransientDataAccessException"/>
 *   </batch:retryable-exception-classes>
 *
 * </batch:chunk>
 * }</pre>
 *
 * <p>No Spring annotations — this class exists for documentation and
 * programmatic reference only; XML jobs list the classes directly.
 */
public final class BatchExceptionClasses {

    private BatchExceptionClasses() {}

    // ---------------------------------------------------------------
    // TRANSIENT  — retry then skip
    // ---------------------------------------------------------------

    /**
     * Standard JDBC transient exception — lock timeouts, connection blips.
     * Retryable + skippable.
     */
    public static final Class<SQLTransientException> TRANSIENT_SQL =
            SQLTransientException.class;

    /**
     * Spring Data transient exception — covers DeadlockLoserDataAccessException,
     * QueryTimeoutException, etc.
     * Retryable + skippable.
     */
    public static final Class<TransientDataAccessException> TRANSIENT_DATA_ACCESS =
            TransientDataAccessException.class;

    // ---------------------------------------------------------------
    // PERMANENT  — skip immediately, no retry
    // ---------------------------------------------------------------

    /**
     * Bad input data — null fields, wrong format, out-of-range values.
     * Skippable only.
     */
    public static final Class<IllegalArgumentException> PERMANENT_BAD_INPUT =
            IllegalArgumentException.class;

    /**
     * Invalid business state — item arrived in unexpected state.
     * Skippable only.
     */
    public static final Class<IllegalStateException> PERMANENT_BAD_STATE =
            IllegalStateException.class;

    /**
     * DB constraint violation — duplicate key, FK violation, check constraint.
     * Skippable only (retrying will produce the same violation).
     */
    public static final Class<DataIntegrityViolationException> PERMANENT_CONSTRAINT =
            DataIntegrityViolationException.class;
}
