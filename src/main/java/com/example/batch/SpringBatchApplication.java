package com.example.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.job.AbstractJob;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Main entry point for the Spring Batch framework.
 *
 * <h3>Job slug convention</h3>
 * Pass just the job name slug — the framework resolves everything:
 * <pre>
 *   "invoice-job"  →  XML  spring/jobs/invoice-job.xml
 *                  →  bean invoiceJob  (kebab → camelCase)
 * </pre>
 *
 * <h3>Usage</h3>
 * <pre>
 *   java [-Dkey=value ...] -jar app.jar &lt;job-slug&gt; [key=value ...]
 *
 *   # Minimal
 *   java -jar app.jar invoice-job
 *
 *   # With job parameters
 *   java -jar app.jar invoice-job  batchDate=2026-01-15  retryMax=3
 *
 *   # With profile  (loads application-prod.properties from classpath)
 *   java -Dapp.profile=prod  -jar app.jar invoice-job
 *
 *   # With external override file
 *   java -Dapp.config.file=/etc/batch/prod.properties  -jar app.jar invoice-job
 *
 *   # Dry-run: load context + validate config, then exit (no job runs)
 *   java -jar app.jar --dry-run invoice-job  batchDate=2026-01-15
 *
 *   # Backward-compat: explicit XML path + bean name still works
 *   java -jar app.jar spring/jobs/invoice-job.xml invoiceJob batchDate=2026-01-15
 * </pre>
 *
 * <h3>Property priority (highest → lowest)</h3>
 * <ol>
 *   <li>CLI {@code key=value} params  — injected into Spring env + JobParameters</li>
 *   <li>JVM {@code -Dkey=value} system properties</li>
 *   <li>OS environment variables</li>
 *   <li>External file  {@code -Dapp.config.file=/path/override.properties}</li>
 *   <li>Profile file   {@code -Dapp.profile=prod} → application-prod.properties</li>
 *   <li>{@code application.properties}  (base, lowest priority)</li>
 * </ol>
 *
 * <h3>Exit codes</h3>
 * 0 = COMPLETED / DRY-RUN PASSED · 1 = FAILED / STOPPED · 2 = bad args / startup error / DRY-RUN FAILED
 */
public class SpringBatchApplication {

    private static final Logger log = LoggerFactory.getLogger(SpringBatchApplication.class);

    /**
     * Shared infrastructure XMLs loaded for every job — unchanged when you add new jobs.
     * <ul>
     *   <li>{@code batch-infrastructure.xml} — datasources, transaction managers, job repository</li>
     *   <li>{@code batch-mail.xml}           — SMTP / JavaMailSender</li>
     *   <li>{@code batch-listeners.xml}      — statistics tasklet, step/job listeners, error collector</li>
     *   <li>{@code batch-step-defaults.xml}  — abstract executor parent bean for multi-threaded steps</li>
     * </ul>
     */
    private static final String[] SHARED_XML = {
        "spring/batch-infrastructure.xml",
        "spring/batch-mail.xml",
        "spring/batch-listeners.xml",
        "spring/batch-step-defaults.xml"
    };

    private static final String JOB_XML_DIR    = "spring/jobs/";
    private static final String JOB_XML_SUFFIX = ".xml";

    // ---------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------

    public static void main(String[] args) {
        System.exit(run(args));
    }

    /** Package-visible so tests can invoke without System.exit. */
    static int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 2;
        }
        boolean dryRun   = isDryRun(args);
        String[] cleaned = stripFlags(args);   // remove --dry-run / --validate
        if (cleaned.length == 0) {
            printUsage();
            return 2;
        }
        try {
            return execute(cleaned, dryRun);
        } catch (Exception ex) {
            log.error("Fatal startup error: {}", ex.getMessage(), ex);
            return 2;
        }
    }

    // ---------------------------------------------------------------
    // Core execution
    // ---------------------------------------------------------------

    private static int execute(String[] args, boolean dryRun) throws Exception {

        String input    = args[0];
        String xmlPath  = resolveXmlPath(input);
        String beanHint = resolveBeanHint(input, args);   // null = auto-detect
        int paramStart  = paramStartIndex(input, args);
        Map<String, String> cliParams = parseCLIParams(args, paramStart);

        // ── Correlation ID: short unique ID threaded through every log line ──
        String correlationId = UUID.randomUUID().toString()
                                   .replace("-", "").substring(0, 8).toUpperCase();
        MDC.put("correlationId", correlationId);
        MDC.put("jobName", beanHint != null ? beanHint : "unknown");

        try {
            log.info("=================================================");
            if (dryRun) log.info("  *** DRY-RUN MODE — job will NOT execute ***");
            log.info("  Correlation ID : {}", correlationId);
            log.info("  Job XML        : {}", xmlPath);
            log.info("  Job Bean       : {}", beanHint != null ? beanHint : "(auto-detect)");
            log.info("  CLI Params     : {}", cliParams.isEmpty() ? "(none)" : cliParams);
            log.info("=================================================");

            // GenericXmlApplicationContext lets us inject property sources
            // BEFORE the XML is parsed so ${placeholder} values resolve correctly.
            try (GenericXmlApplicationContext ctx = new GenericXmlApplicationContext()) {

                enrichEnvironment(ctx.getEnvironment().getPropertySources(), cliParams);

                ctx.load(buildContextFiles(xmlPath));
                ctx.refresh();

                // Config hash — computed after refresh so all property sources are merged
                String configHash = computeConfigHash(ctx.getEnvironment().getPropertySources());
                log.info("Config hash: {}", configHash);

                String beanName = (beanHint != null) ? beanHint : autoDetectJobBean(ctx, xmlPath);
                Job         job = ctx.getBean(beanName, Job.class);

                if (dryRun) {
                    return executeDryRun(job, beanName, xmlPath, cliParams, correlationId, configHash);
                }

                JobLauncher launcher = ctx.getBean("jobLauncher", JobLauncher.class);

                JobParameters params = buildJobParameters(cliParams, correlationId, configHash);
                log.info("Launching '{}' with params: {}", beanName, params);

                JobExecution exec = launcher.run(job, params);
                log.info("Finished '{}' — status={} exit={} correlationId={}",
                         beanName, exec.getStatus(), exec.getExitStatus().getExitCode(),
                         correlationId);

                return exec.getStatus().isUnsuccessful() ? 1 : 0;
            }
        } finally {
            MDC.clear();
        }
    }

    // ---------------------------------------------------------------
    // Dry-run validation
    // ---------------------------------------------------------------

    /**
     * Validates the job configuration without executing it.
     *
     * <ol>
     *   <li>Context already loaded and refreshed by the caller — all {@code ${placeholder}}
     *       values are resolved.</li>
     *   <li>Runs {@link org.springframework.batch.core.JobParametersValidator} with
     *       the same parameters that would be used in a real run.</li>
     *   <li>Lists every step registered in the job via {@link StepLocator}.</li>
     *   <li>Prints a structured PASS / FAIL report to the log.</li>
     * </ol>
     *
     * @return 0 if validation passed, 2 if validation failed
     */
    private static int executeDryRun(Job job,
                                     String beanName,
                                     String xmlPath,
                                     Map<String, String> cliParams,
                                     String correlationId,
                                     String configHash) {
        String SEP  = "=".repeat(65);
        String THIN = "-".repeat(65);

        log.info(SEP);
        log.info("  DRY-RUN VALIDATION REPORT");
        log.info(SEP);

        List<String> issues = new ArrayList<>();

        // 1. Context load — already succeeded (we got here), report it
        log.info("  [PASS] Spring context loaded from: {}", xmlPath);

        // 2. Job bean
        log.info("  [PASS] Job bean '{}' resolved successfully", beanName);

        // 3. Config hash — proves placeholders resolved
        log.info("  [INFO] Config hash : {}", configHash);
        log.info("  [INFO] Correlation  : {}", correlationId);

        // 4. Parameter validation
        log.info(THIN);
        log.info("  PARAMETER VALIDATION");
        log.info(THIN);
        JobParameters params = buildJobParameters(cliParams, correlationId, configHash);
        log.info("  Parameters supplied: {}", params);
        try {
            job.getJobParametersValidator().validate(params);
            log.info("  [PASS] JobParametersValidator — all checks passed");
        } catch (JobParametersInvalidException ex) {
            log.error("  [FAIL] JobParametersValidator — {}", ex.getMessage());
            issues.add("Parameter validation: " + ex.getMessage());
        }

        // 5. Step graph — enumerate via StepLocator if supported
        log.info(THIN);
        log.info("  STEP GRAPH");
        log.info(THIN);
        if (job instanceof AbstractJob aj) {
            Collection<String> stepNames = aj.getStepNames();
            if (stepNames.isEmpty()) {
                log.warn("  [WARN] No steps found in job '{}'", beanName);
                issues.add("No steps registered in job '" + beanName + "'");
            } else {
                int i = 1;
                for (String stepName : stepNames) {
                    log.info("  [PASS] Step {}: {}", i++, stepName);
                }
            }
        } else {
            log.info("  [INFO] Job type '{}' — step graph not inspectable", job.getClass().getSimpleName());
        }

        // 6. Summary
        log.info(SEP);
        if (issues.isEmpty()) {
            log.info("  RESULT: PASSED — configuration is valid");
            log.info("  Run without --dry-run to execute the job.");
        } else {
            log.error("  RESULT: FAILED — {} issue(s) found", issues.size());
            for (String issue : issues) {
                log.error("    * {}", issue);
            }
        }
        log.info(SEP);

        return issues.isEmpty() ? 0 : 2;
    }

    /** Returns {@code true} if any arg is {@code --dry-run} or {@code --validate}. */
    private static boolean isDryRun(String[] args) {
        for (String arg : args) {
            if ("--dry-run".equalsIgnoreCase(arg) || "--validate".equalsIgnoreCase(arg)) return true;
        }
        return false;
    }

    /** Returns a copy of {@code args} with all {@code --*} flag tokens removed. */
    private static String[] stripFlags(String[] args) {
        List<String> cleaned = new ArrayList<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) cleaned.add(arg);
        }
        return cleaned.toArray(new String[0]);
    }

    // ---------------------------------------------------------------
    // Property source enrichment
    // ---------------------------------------------------------------

    /**
     * Injects additional property sources before context refresh.
     *
     * <p>Priority order (lowest → highest within our additions):
     * <ol>
     *   <li>Profile file  {@code application-{profile}.properties}  from classpath</li>
     *   <li>External override file  {@code -Dapp.config.file=...}</li>
     *   <li>CLI {@code key=value} params</li>
     * </ol>
     * JVM system properties and OS env vars are already at the top of the
     * Spring environment and are untouched.
     */
    private static void enrichEnvironment(MutablePropertySources sources,
                                          Map<String, String> cliParams) {
        String lastAdded = null;

        // 1. Profile-specific classpath file (lowest of our additions)
        String profile = System.getProperty("app.profile", "").trim();
        if (!profile.isBlank()) {
            String name  = "application-" + profile + ".properties";
            Properties p = loadClasspathProperties(name);
            if (!p.isEmpty()) {
                sources.addLast(new PropertiesPropertySource("classpath:" + name, p));
                lastAdded = "classpath:" + name;
                log.info("Loaded profile config: {} ({} keys)", name, p.size());
            } else {
                log.warn("Profile config not found on classpath: {}", name);
            }
        }

        // 2. External override file (-Dapp.config.file=...)
        String extPath = System.getProperty("app.config.file", "").trim();
        if (!extPath.isBlank()) {
            Properties p = loadFileSystemProperties(extPath);
            if (!p.isEmpty()) {
                PropertiesPropertySource ps =
                    new PropertiesPropertySource("external:" + extPath, p);
                if (lastAdded != null) sources.addBefore(lastAdded, ps);
                else                   sources.addLast(ps);
                lastAdded = "external:" + extPath;
                log.info("Loaded external config: {} ({} keys)", extPath, p.size());
            }
        }

        // 3. CLI key=value overrides (highest of our additions)
        if (!cliParams.isEmpty()) {
            Map<String, Object> cliMap = new LinkedHashMap<>(cliParams);
            MapPropertySource ps = new MapPropertySource("cli:params", cliMap);
            if (lastAdded != null) sources.addBefore(lastAdded, ps);
            else                   sources.addLast(ps);
            log.info("Applied {} CLI param(s) as property overrides.", cliMap.size());
        }
    }

    private static Properties loadClasspathProperties(String name) {
        ClassPathResource res = new ClassPathResource(name);
        if (!res.exists()) return new Properties();
        Properties p = new Properties();
        try (InputStream in = res.getInputStream()) { p.load(in); }
        catch (IOException e) { log.warn("Cannot load classpath:{} — {}", name, e.getMessage()); }
        return p;
    }

    private static Properties loadFileSystemProperties(String path) {
        File file = new File(path);
        if (!file.exists()) {
            log.warn("External config not found: {}", file.getAbsolutePath());
            return new Properties();
        }
        Properties p = new Properties();
        try (InputStream in = new FileSystemResource(file).getInputStream()) { p.load(in); }
        catch (IOException e) { log.warn("Cannot load {}: {}", path, e.getMessage()); }
        return p;
    }

    // ---------------------------------------------------------------
    // Slug / path resolution
    // ---------------------------------------------------------------

    /**
     * Converts a slug or explicit XML path to a classpath resource path.
     * <pre>
     *   "invoice-job"                 → "spring/jobs/invoice-job.xml"
     *   "spring/jobs/invoice-job.xml" →  unchanged
     * </pre>
     */
    private static String resolveXmlPath(String input) {
        if (isExplicitPath(input)) return input;
        return JOB_XML_DIR + input + JOB_XML_SUFFIX;
    }

    /**
     * Derives camelCase bean name from slug, or reads it from args[1].
     * Returns {@code null} to trigger auto-detection.
     * <pre>
     *   "invoice-job"  → "invoiceJob"
     *   "customer-etl" → "customerEtl"
     * </pre>
     */
    private static String resolveBeanHint(String input, String[] args) {
        if (args.length >= 2 && !args[1].contains("=")) return args[1]; // explicit bean name
        if (!isExplicitPath(input)) return kebabToCamel(input);
        return null; // explicit path, no hint → auto-detect
    }

    private static int paramStartIndex(String input, String[] args) {
        if (args.length >= 2 && !args[1].contains("=")) return 2;
        return 1;
    }

    private static boolean isExplicitPath(String s) {
        return s.endsWith(".xml") || s.contains("/");
    }

    /** "invoice-job" → "invoiceJob",  "customer-etl-v2" → "customerEtlV2" */
    static String kebabToCamel(String kebab) {
        String[] parts = kebab.split("-");
        if (parts.length == 1) return parts[0];
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Parameter / context helpers
    // ---------------------------------------------------------------

    private static Map<String, String> parseCLIParams(String[] args, int startIndex) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = startIndex; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) params.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            else        log.warn("Ignoring malformed argument (no '='): {}", args[i]);
        }
        return params;
    }

    private static JobParameters buildJobParameters(Map<String, String> cliParams,
                                                    String correlationId,
                                                    String configHash) {
        JobParametersBuilder b = new JobParametersBuilder()
                .addLong  ("run.id",        System.currentTimeMillis())
                .addString("correlationId", correlationId)
                .addString("configHash",    configHash);
        cliParams.forEach(b::addString);
        return b.toJobParameters();
    }

    /**
     * Computes a short SHA-256 hash of non-sensitive {@code batch.*},
     * {@code datasource.url}, {@code datasource.username}, and {@code mail.smtp.*}
     * properties.  Lets you detect config changes between runs.
     */
    private static String computeConfigHash(MutablePropertySources sources) {
        String[] includePrefix   = {"batch.", "datasource.url", "datasource.username",
                                    "mail.smtp.host", "mail.smtp.port"};
        String[] sensitiveTokens = {"password", "secret", "key", "token", "credential"};

        TreeMap<String, String> snapshot = new TreeMap<>();
        for (var ps : sources) {
            if (ps instanceof EnumerablePropertySource<?> eps) {
                for (String name : eps.getPropertyNames()) {
                    String lower = name.toLowerCase();
                    if (!matchesAny(lower, includePrefix)) continue;
                    if (matchesAny(lower, sensitiveTokens)) continue;
                    Object val = eps.getProperty(name);
                    snapshot.putIfAbsent(name, val != null ? val.toString() : "");
                }
            }
        }

        if (snapshot.isEmpty()) return "NO-CONFIG";

        StringBuilder content = new StringBuilder();
        snapshot.forEach((k, v) -> content.append(k).append('=').append(v).append('\n'));

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                                       .digest(content.toString()
                                                      .getBytes(StandardCharsets.UTF_8));
            // Return first 10 hex chars — enough for change detection
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 5; i++) hex.append(String.format("%02X", hash[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return "HASH-ERR";
        }
    }

    private static boolean matchesAny(String s, String[] tokens) {
        for (String t : tokens) { if (s.contains(t)) return true; }
        return false;
    }

    private static String[] buildContextFiles(String jobXmlPath) {
        String[] all = Arrays.copyOf(SHARED_XML, SHARED_XML.length + 1);
        all[all.length - 1] = jobXmlPath;
        return all;
    }

    private static String autoDetectJobBean(GenericXmlApplicationContext ctx, String xmlPath) {
        Map<String, Job> jobs = ctx.getBeansOfType(Job.class);
        if (jobs.isEmpty())
            throw new IllegalStateException("No Job bean found in context loaded from: " + xmlPath);
        if (jobs.size() > 1)
            throw new IllegalStateException(
                "Multiple Job beans " + jobs.keySet() + " in: " + xmlPath
                + " — pass the bean name as the second argument.");
        String name = jobs.keySet().iterator().next();
        log.info("Auto-detected job bean: '{}'", name);
        return name;
    }

    // ---------------------------------------------------------------
    // Usage
    // ---------------------------------------------------------------

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  java [-Dkey=value ...] -jar app.jar [--dry-run] <job-slug> [key=value ...]");
        System.err.println();
        System.err.println("Slug convention:");
        System.err.println("  invoice-job   →  spring/jobs/invoice-job.xml  +  bean: invoiceJob");
        System.err.println("  customer-etl  →  spring/jobs/customer-etl.xml +  bean: customerEtl");
        System.err.println();
        System.err.println("Job parameters (appended after slug):");
        System.err.println("  java -jar app.jar invoice-job  batchDate=2026-01-15  retryMax=3");
        System.err.println("  Available in XML via  #{jobParameters['batchDate']}");
        System.err.println("  Also override any  ${key}  from application.properties");
        System.err.println();
        System.err.println("Dry-run / validate mode (loads context + validates, no job executed):");
        System.err.println("  java -jar app.jar --dry-run invoice-job  batchDate=2026-01-15");
        System.err.println("  java -jar app.jar --validate invoice-job");
        System.err.println("  Exit 0 = PASSED, 2 = FAILED (context error or param validation failure)");
        System.err.println();
        System.err.println("Config flags (-D before -jar):");
        System.err.println("  -Dapp.profile=prod              loads classpath:application-prod.properties");
        System.err.println("  -Dapp.config.file=/path/f.props loads an external override file");
        System.err.println();
        System.err.println("Backward-compat (explicit XML path):");
        System.err.println("  java -jar app.jar spring/jobs/invoice-job.xml invoiceJob batchDate=2026-01-15");
        System.err.println();
    }
}
