package com.example.batch.multithreaded;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkItemProcessorTest {

    private WorkItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WorkItemProcessor();
    }

    private WorkItem item(long id, String payload) {
        WorkItem w = new WorkItem();
        w.setId(id);
        w.setPayload(payload);
        w.setPriority(1);
        w.setStatus("PENDING");
        w.setCreatedAt(LocalDateTime.now());
        return w;
    }

    // ---------------------------------------------------------------
    // Payload validation
    // ---------------------------------------------------------------

    @Test
    void process_nullPayload_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(item(1L, null)));
        assertTrue(ex.getMessage().contains("empty payload"));
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    void process_blankPayload_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(item(2L, "   ")));
    }

    @Test
    void process_emptyPayload_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(item(3L, "")));
    }

    // ---------------------------------------------------------------
    // Successful processing
    // ---------------------------------------------------------------

    @Test
    void process_validPayload_uppercasesPayload() {
        WorkItem result = processor.process(item(4L, "hello world"));
        assertNotNull(result);
        assertEquals("HELLO WORLD", result.getPayload());
    }

    @Test
    void process_validPayload_setsStatusToProcessed() {
        WorkItem result = processor.process(item(5L, "do work"));
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
    }

    @Test
    void process_validPayload_setsProcessedAtToNow() {
        LocalDateTime before = LocalDateTime.now();
        WorkItem result = processor.process(item(6L, "some payload"));
        LocalDateTime after = LocalDateTime.now();

        assertNotNull(result);
        assertNotNull(result.getProcessedAt());
        assertFalse(result.getProcessedAt().isBefore(before),
                "processedAt should not be before method was called");
        assertFalse(result.getProcessedAt().isAfter(after),
                "processedAt should not be after method returned");
    }

    @Test
    void process_validPayload_trimsBefore_uppercase() {
        WorkItem result = processor.process(item(7L, "  trim me  "));
        assertNotNull(result);
        assertEquals("TRIM ME", result.getPayload());
    }

    @Test
    void process_validPayload_returnsSameInstance() {
        WorkItem w = item(8L, "payload");
        assertSame(w, processor.process(w));
    }

    @Test
    void process_stateless_independentResults() {
        // Verify no state leaks across calls (thread-safety property)
        WorkItem a = processor.process(item(9L, "task a"));
        WorkItem b = processor.process(item(10L, "task b"));
        assertEquals("TASK A", a.getPayload());
        assertEquals("TASK B", b.getPayload());
    }
}
