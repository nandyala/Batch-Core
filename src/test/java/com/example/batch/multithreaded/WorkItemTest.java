package com.example.batch.multithreaded;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemTest {

    @Test
    void defaultConstructor_primitiveFieldsAreDefault() {
        WorkItem item = new WorkItem();
        assertEquals(0L, item.getId());
        assertNull(item.getPayload());
        assertEquals(0, item.getPriority());
        assertNull(item.getStatus());
        assertNull(item.getCreatedAt());
        assertNull(item.getProcessedAt());
    }

    @Test
    void setters_roundTrip_allFields() {
        LocalDateTime created   = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        LocalDateTime processed = LocalDateTime.of(2026, 1, 15, 10, 31, 0);

        WorkItem item = new WorkItem();
        item.setId(42L);
        item.setPayload("do something important");
        item.setPriority(5);
        item.setStatus("PENDING");
        item.setCreatedAt(created);
        item.setProcessedAt(processed);

        assertEquals(42L, item.getId());
        assertEquals("do something important", item.getPayload());
        assertEquals(5, item.getPriority());
        assertEquals("PENDING", item.getStatus());
        assertEquals(created, item.getCreatedAt());
        assertEquals(processed, item.getProcessedAt());
    }

    @Test
    void processedAt_acceptsNull() {
        WorkItem item = new WorkItem();
        item.setProcessedAt(null);
        assertNull(item.getProcessedAt());
    }

    @Test
    void createdAt_acceptsNull() {
        WorkItem item = new WorkItem();
        item.setCreatedAt(null);
        assertNull(item.getCreatedAt());
    }
}
