package com.example.batch.multithreaded;

import java.time.LocalDateTime;

/**
 * Represents one row from the WORK_ITEM table.
 * Read by {@link WorkItemRowMapper} and processed by {@link WorkItemProcessor}.
 *
 * <p>Expected table definition:
 * <pre>
 *   WORK_ITEM (
 *     id            BIGINT PRIMARY KEY,
 *     payload       NVARCHAR(2000),
 *     priority      INT DEFAULT 0,
 *     status        VARCHAR(20),   -- PENDING | PROCESSED | FAILED
 *     created_at    DATETIME2,
 *     processed_at  DATETIME2 NULL
 *   )
 * </pre>
 */
public class WorkItem {

    private long          id;
    private String        payload;
    private int           priority;
    private String        status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public long          getId()                        { return id; }
    public void          setId(long v)                  { this.id = v; }

    public String        getPayload()                   { return payload; }
    public void          setPayload(String v)           { this.payload = v; }

    public int           getPriority()                  { return priority; }
    public void          setPriority(int v)             { this.priority = v; }

    public String        getStatus()                    { return status; }
    public void          setStatus(String v)            { this.status = v; }

    public LocalDateTime getCreatedAt()                 { return createdAt; }
    public void          setCreatedAt(LocalDateTime v)  { this.createdAt = v; }

    public LocalDateTime getProcessedAt()               { return processedAt; }
    public void          setProcessedAt(LocalDateTime v){ this.processedAt = v; }
}
