package com.example.batch.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ColumnRangePartitioner} using an H2 in-memory database.
 *
 * <p>H2 is already on the test classpath (via the project pom.xml) and avoids
 * any real SQL Server dependency. Tests cover:
 * <ul>
 *   <li>Normal partitioning across full grid size</li>
 *   <li>Empty table → single empty partition</li>
 *   <li>Single row → single partition</li>
 *   <li>Range smaller than gridSize → fewer partitions created</li>
 *   <li>WHERE clause filtering</li>
 * </ul>
 */
class ColumnRangePartitionerTest {

    private EmbeddedDatabase db;
    private JdbcTemplate jdbc;
    private ColumnRangePartitioner partitioner;

    @BeforeEach
    void setUp() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
        jdbc = new JdbcTemplate(db);

        partitioner = new ColumnRangePartitioner();
        partitioner.setDataSource(db);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // ---------------------------------------------------------------
    // Normal partitioning — full grid
    // ---------------------------------------------------------------

    @Test
    void partition_standardRange_createsRequestedPartitions() {
        createTableWithIds(1, 100);  // 100 rows, IDs 1–100

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertEquals(4, partitions.size(),
                "Should create exactly 4 partitions for a large enough range");

        // Every partitionN key must be present
        assertTrue(partitions.containsKey("partition0"));
        assertTrue(partitions.containsKey("partition1"));
        assertTrue(partitions.containsKey("partition2"));
        assertTrue(partitions.containsKey("partition3"));
    }

    @Test
    void partition_allRowsCovered_noGapsOrOverlaps() {
        createTableWithIds(1, 100);

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        // minId of partition0 must equal 1 (global min)
        long first = partitions.get("partition0").getLong("minId");
        assertEquals(1L, first, "First partition must start at the global minimum (1)");

        // maxId of last partition must equal 100 (global max)
        long lastMax = partitions.get("partition3").getLong("maxId");
        assertEquals(100L, lastMax, "Last partition must end at the global maximum (100)");

        // Each partition must carry a partitionNumber (used in file naming)
        for (int i = 0; i < 4; i++) {
            int partNum = partitions.get("partition" + i).getInt("partitionNumber");
            assertEquals(i, partNum, "partitionNumber must match partition index");
        }
    }

    @Test
    void partition_rangesAreContiguous() {
        createTableWithIds(10, 50);  // IDs 10–50

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        long prevMax = -1;
        for (int i = 0; i < partitions.size(); i++) {
            ExecutionContext ctx = partitions.get("partition" + i);
            long min = ctx.getLong("minId");
            long max = ctx.getLong("maxId");

            // min must immediately follow prev max (no gap)
            if (prevMax >= 0) {
                assertEquals(prevMax + 1, min,
                        "Partition " + i + " minId should be prevMax+1, no gaps allowed");
            }
            assertTrue(min <= max,
                    "minId must be <= maxId in partition " + i);
            prevMax = max;
        }
    }

    // ---------------------------------------------------------------
    // Empty table → single empty partition
    // ---------------------------------------------------------------

    @Test
    void partition_emptyTable_returnsSingleEmptyPartition() {
        jdbc.execute("CREATE TABLE TEST_TABLE (id BIGINT)");
        // No rows inserted

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertEquals(1, partitions.size(),
                "Empty table should produce exactly one empty partition");

        ExecutionContext ctx = partitions.get("partition0");
        assertNotNull(ctx);

        long min = ctx.getLong("minId");
        long max = ctx.getLong("maxId");

        assertTrue(min > max,
                "Empty partition must have an impossible range (minId > maxId): min=" + min + " max=" + max);
    }

    // ---------------------------------------------------------------
    // Fewer rows than gridSize → fewer partitions
    // ---------------------------------------------------------------

    @Test
    void partition_fewerRowsThanGridSize_fewerPartitionsCreated() {
        createTableWithIds(1, 3);   // only 3 distinct IDs

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(10);

        // Can't have more partitions than IDs
        assertTrue(partitions.size() <= 3,
                "Should not create more partitions than distinct ID values");
        assertFalse(partitions.isEmpty());
    }

    // ---------------------------------------------------------------
    // Single row → single partition covering that row
    // ---------------------------------------------------------------

    @Test
    void partition_singleRow_singlePartition() {
        jdbc.execute("CREATE TABLE TEST_TABLE (id BIGINT)");
        jdbc.execute("INSERT INTO TEST_TABLE VALUES (42)");

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertEquals(1, partitions.size());

        ExecutionContext ctx = partitions.get("partition0");
        assertEquals(42L, ctx.getLong("minId"));
        assertEquals(42L, ctx.getLong("maxId"));
    }

    // ---------------------------------------------------------------
    // gridSize = 1 → always one partition
    // ---------------------------------------------------------------

    @Test
    void partition_gridSizeOne_alwaysOnePartition() {
        createTableWithIds(1, 1000);

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");

        Map<String, ExecutionContext> partitions = partitioner.partition(1);

        assertEquals(1, partitions.size());
        ExecutionContext ctx = partitions.get("partition0");
        assertEquals(1L,    ctx.getLong("minId"));
        assertEquals(1000L, ctx.getLong("maxId"));
    }

    // ---------------------------------------------------------------
    // WHERE clause filtering
    // ---------------------------------------------------------------

    @Test
    void partition_withWhereClause_onlyMatchingRowsConsidered() {
        jdbc.execute("CREATE TABLE TEST_TABLE (id BIGINT, status VARCHAR(20))");
        for (int i = 1; i <= 10; i++) {
            String status = (i <= 5) ? "PENDING" : "PROCESSED";
            jdbc.execute("INSERT INTO TEST_TABLE VALUES (" + i + ", '" + status + "')");
        }

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");
        partitioner.setWhereClause("status = 'PENDING'");  // IDs 1–5 only

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        // With IDs 1–5 and gridSize 4: at most 4 partitions, max id should be 5
        long globalMin = Long.MAX_VALUE;
        long globalMax = Long.MIN_VALUE;
        for (ExecutionContext ctx : partitions.values()) {
            long min = ctx.getLong("minId");
            long max = ctx.getLong("maxId");
            if (min < globalMin) globalMin = min;
            if (max > globalMax) globalMax = max;
        }

        assertEquals(1L, globalMin, "Min should be 1 (first PENDING id)");
        assertEquals(5L, globalMax, "Max should be 5 (last PENDING id)");
    }

    @Test
    void partition_whereClauseMatchesNothing_returnsEmptyPartition() {
        jdbc.execute("CREATE TABLE TEST_TABLE (id BIGINT, status VARCHAR(20))");
        jdbc.execute("INSERT INTO TEST_TABLE VALUES (1, 'PROCESSED')");

        partitioner.setTable("TEST_TABLE");
        partitioner.setColumn("id");
        partitioner.setWhereClause("status = 'PENDING'");  // nothing matches

        Map<String, ExecutionContext> partitions = partitioner.partition(4);

        assertEquals(1, partitions.size(), "No matching rows → one empty partition");
        long min = partitions.get("partition0").getLong("minId");
        long max = partitions.get("partition0").getLong("maxId");
        assertTrue(min > max, "Empty partition must have impossible range");
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** Creates TEST_TABLE and inserts rows with sequential IDs from start to end (inclusive). */
    private void createTableWithIds(int start, int end) {
        jdbc.execute("CREATE TABLE TEST_TABLE (id BIGINT)");
        for (int i = start; i <= end; i++) {
            jdbc.execute("INSERT INTO TEST_TABLE VALUES (" + i + ")");
        }
    }
}
