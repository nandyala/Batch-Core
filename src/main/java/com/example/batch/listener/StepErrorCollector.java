package com.example.batch.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.listener.SkipListenerSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures skip events per step and stores them in the step's
 * {@code ExecutionContext} under key {@value #ERRORS_KEY}.
 *
 * <p>Implements both {@link StepExecutionListener} (to capture the current
 * step reference in {@code beforeStep}) and {@link SkipListenerSupport}
 * (to record each skipped item and its exception).
 *
 * <p>The stored {@code List<String>} is capped at {@value #MAX_ERRORS} entries
 * so the execution context stays compact.  The
 * {@link com.example.batch.tasklet.StatisticsAndEmailTasklet} reads this list
 * after all steps finish and includes the errors in the log + email report.
 *
 * <h3>XML wiring (in batch-listeners.xml)</h3>
 * <pre>{@code
 * <bean id="stepErrorCollector"
 *       class="com.example.batch.listener.StepErrorCollector"/>
 * }</pre>
 *
 * <h3>Usage in any chunk step</h3>
 * <pre>{@code
 * <batch:tasklet ...>
 *   <batch:chunk ...>
 *     <batch:skippable-exception-classes>...</batch:skippable-exception-classes>
 *     <batch:retryable-exception-classes>...</batch:retryable-exception-classes>
 *   </batch:chunk>
 *   <batch:listeners>
 *     <batch:listener ref="stepStatisticsListener"/>
 *     <batch:listener ref="stepErrorCollector"/>
 *   </batch:listeners>
 * </batch:tasklet>
 * }</pre>
 *
 * <p>No Spring annotations — wired via XML.
 */
public class StepErrorCollector extends SkipListenerSupport<Object, Object>
        implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(StepErrorCollector.class);

    /** Key used in {@code StepExecution.getExecutionContext()} to store the error list. */
    public static final String ERRORS_KEY = "step.errors";

    /** Maximum number of error messages stored per step (keeps the context compact). */
    public static final int MAX_ERRORS = 10;

    /**
     * Holds the current step's execution — set in {@link #beforeStep}.
     * Safe for synchronous (non-parallel) job execution.
     */
    private StepExecution stepExecution;

    // ---------------------------------------------------------------
    // StepExecutionListener
    // ---------------------------------------------------------------

    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return null; // do not override step exit status
    }

    // ---------------------------------------------------------------
    // SkipListenerSupport  (override only the three onSkipIn* methods)
    // ---------------------------------------------------------------

    @Override
    public void onSkipInRead(Throwable t) {
        record("Read skip — " + classify(t) + ": " + summarize(t));
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        record("Process skip — " + classify(t) + ": " + summarize(t));
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        record("Write skip — " + classify(t) + ": " + summarize(t));
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    private void record(String message) {
        if (stepExecution == null) return;

        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) stepExecution.getExecutionContext().get(ERRORS_KEY);

        if (errors == null) {
            errors = new ArrayList<>();
            stepExecution.getExecutionContext().put(ERRORS_KEY, errors);
        }

        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
            log.warn("[{}] {}", stepExecution.getStepName(), message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("... (further errors suppressed — see full logs)");
        }
    }

    /**
     * Classifies an exception as TRANSIENT or PERMANENT for the error message.
     * Transient = might succeed on retry; Permanent = always fails for this item.
     */
    private static String classify(Throwable t) {
        if (t instanceof java.sql.SQLTransientException
                || t instanceof org.springframework.dao.TransientDataAccessException) {
            return "TRANSIENT";
        }
        return "PERMANENT";
    }

    private static String summarize(Throwable t) {
        String msg = t.getMessage();
        String text = (msg != null && !msg.isBlank()) ? msg : "(no message)";
        // Truncate long messages (e.g. SQL errors with full stack details)
        return t.getClass().getSimpleName() + " — "
               + (text.length() > 150 ? text.substring(0, 150) + "…" : text);
    }
}
