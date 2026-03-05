package com.example.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersValidator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Set;

/**
 * Deterministic parameter validation for Spring Batch jobs.
 *
 * <h3>Built-in validation rules</h3>
 * <ul>
 *   <li><b>Required params</b> — configurable set of parameter names that
 *       must be present; throws if any are missing.</li>
 *   <li><b>batchDate</b> — if present, must be {@code yyyy-MM-dd} format.</li>
 * </ul>
 *
 * <h3>XML wiring — shared validator (no required params)</h3>
 * <pre>{@code
 * <!-- in batch-listeners.xml -->
 * <bean id="batchJobParameterValidator"
 *       class="com.example.batch.support.BatchJobParameterValidator"/>
 * }</pre>
 *
 * <h3>XML wiring — per-job validator with required params</h3>
 * <pre>{@code
 * <!-- in invoice-job.xml -->
 * <bean id="invoiceJobParameterValidator"
 *       class="com.example.batch.support.BatchJobParameterValidator">
 *   <property name="requiredParams">
 *     <set>
 *       <value>batchDate</value>
 *     </set>
 *   </property>
 * </bean>
 *
 * <batch:job id="invoiceJob" restartable="true">
 *   <batch:validator ref="invoiceJobParameterValidator"/>
 *   ...
 * </batch:job>
 * }</pre>
 *
 * <p>No Spring annotations — wired entirely via XML.
 */
public class BatchJobParameterValidator implements JobParametersValidator {

    private static final Logger log = LoggerFactory.getLogger(BatchJobParameterValidator.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Parameter names that MUST be present — injected via XML. */
    private Set<String> requiredParams = Collections.emptySet();

    // ---------------------------------------------------------------
    // JobParametersValidator
    // ---------------------------------------------------------------

    @Override
    public void validate(JobParameters parameters) throws InvalidJobParametersException {

        // 1. Required parameter presence check
        for (String name : requiredParams) {
            if (parameters.getString(name) == null) {
                throw new InvalidJobParametersException(
                    "Missing required job parameter: '" + name + "'. "
                    + "Pass it on the command line: java -jar app.jar <job> " + name + "=<value>");
            }
        }

        // 2. batchDate format validation (if present)
        String batchDate = parameters.getString("batchDate");
        if (batchDate != null) {
            try {
                LocalDate parsed = LocalDate.parse(batchDate, DATE_FMT);
                log.debug("Parameter batchDate validated: {}", parsed);
            } catch (DateTimeParseException e) {
                throw new InvalidJobParametersException(
                    "Invalid batchDate value '" + batchDate + "'. "
                    + "Expected format: yyyy-MM-dd (e.g. 2026-01-15)");
            }
        }

        log.debug("Job parameter validation passed. params={}", parameters);
    }

    // ---------------------------------------------------------------
    // Setter — called by Spring XML <property> injection
    // ---------------------------------------------------------------

    public void setRequiredParams(Set<String> requiredParams) {
        this.requiredParams = requiredParams;
    }
}
