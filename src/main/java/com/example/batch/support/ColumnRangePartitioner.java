package com.example.batch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic {@link Partitioner} that splits a table into ID-range partitions.
 *
 * <p>Queries {@code MIN} and {@code MAX} of a numeric column, then divides
 * the range into {@code gridSize} equal partitions. Each partition receives
 * three values in its {@link ExecutionContext}:
 * <ul>
 *   <li>{@code minId} — inclusive lower bound ({@code long})</li>
 *   <li>{@code maxId} — inclusive upper bound ({@code long})</li>
 *   <li>{@code partitionNumber} — zero-based index ({@code int})</li>
 * </ul>
 *
 * <h3>Usage in a partitioned step reader</h3>
 * <pre>{@code
 * WHERE id BETWEEN #{stepExecutionContext['minId']}
 *               AND #{stepExecutionContext['maxId']}
 * }</pre>
 *
 * <h3>XML configuration</h3>
 * <pre>{@code
 * <bean id="myPartitioner"
 *       class="com.example.batch.support.ColumnRangePartitioner"
 *       scope="step">
 *     <property name="dataSource" ref="dataSource"/>
 *     <property name="table"      value="CUSTOMER_REPORT"/>
 *     <property name="column"     value="id"/>
 *     <!-- optional: restrict the rows being partitioned -->
 *     <property name="whereClause"
 *               value="report_date = CONVERT(date, #{jobParameters['batchDate']}, 23)"/>
 * </bean>
 * }</pre>
 *
 * <h3>Edge cases</h3>
 * <ul>
 *   <li>If the table is empty (MIN/MAX returns NULL) a single empty partition
 *       is returned — the worker step reads zero rows.</li>
 *   <li>If the range is smaller than {@code gridSize}, fewer partitions are
 *       created (one per row at most).</li>
 * </ul>
 */
public class ColumnRangePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(ColumnRangePartitioner.class);

    private JdbcTemplate jdbcTemplate;
    private String       table;
    private String       column;
    private String       whereClause = "";

    // ---------------------------------------------------------------
    // Setters
    // ---------------------------------------------------------------

    public void setDataSource(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** Table name (unqualified or schema-qualified, e.g. {@code dbo.CUSTOMER_REPORT}). */
    public void setTable(String table)             { this.table = table; }

    /** Numeric column to partition by (e.g. {@code id}). Must be indexed. */
    public void setColumn(String column)           { this.column = column; }

    /**
     * Optional SQL WHERE clause (without the {@code WHERE} keyword) to restrict
     * the rows being partitioned, e.g. {@code report_date = '2026-01-15'}.
     */
    public void setWhereClause(String whereClause) { this.whereClause = whereClause; }

    // ---------------------------------------------------------------
    // Partitioner
    // ---------------------------------------------------------------

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {

        String filter  = whereClause.isBlank() ? "" : " WHERE " + whereClause;
        String minSql  = "SELECT MIN(" + column + ") FROM " + table + filter;
        String maxSql  = "SELECT MAX(" + column + ") FROM " + table + filter;

        Long minValue = jdbcTemplate.queryForObject(minSql, Long.class);
        Long maxValue = jdbcTemplate.queryForObject(maxSql, Long.class);

        Map<String, ExecutionContext> partitions = new LinkedHashMap<>();

        if (minValue == null || maxValue == null) {
            log.warn("Table '{}' has no rows matching filter '{}' — returning one empty partition", table, filter);
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId", 0L);
            ctx.putLong("maxId", -1L);        // impossible range → reader returns nothing
            ctx.putInt ("partitionNumber", 0);
            partitions.put("partition0", ctx);
            return partitions;
        }

        long range      = maxValue - minValue;
        // Ensure at least 1 row per partition; cap actual partitions at gridSize
        int  actual     = (int) Math.min(gridSize, range + 1);
        long targetSize = (range / actual) + 1;

        log.info("Partitioning {}.{}: min={} max={} range={} gridSize={} actualPartitions={} targetSize={}",
                 table, column, minValue, maxValue, range, gridSize, actual, targetSize);

        long start = minValue;
        for (int i = 0; i < actual; i++) {
            if (start > maxValue) break;  // all rows already covered by earlier partitions
            // Cap intermediate partition end at maxValue so no partition exceeds the
            // filtered range (important when a WHERE clause limits the min/max bounds).
            long end = (i == actual - 1) ? maxValue : Math.min(start + targetSize - 1, maxValue);

            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId",           start);
            ctx.putLong("maxId",           end);
            ctx.putInt ("partitionNumber", i);

            String key = "partition" + i;
            partitions.put(key, ctx);
            log.debug("  {} → [{}, {}]", key, start, end);

            start = end + 1;
        }

        return partitions;
    }
}
