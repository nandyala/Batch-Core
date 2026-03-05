package com.example.batch.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SampleItemProcessorTest {

    private SampleItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SampleItemProcessor();
    }

    private SampleRecord record(int id, String name) {
        return new SampleRecord(id, name, "user@example.com", "PENDING");
    }

    @Test
    void process_idDivisibleBy10_returnsNullFiltered() throws Exception {
        assertNull(processor.process(record(10, "Customer 10")));
    }

    @Test
    void process_idDivisibleBy10_otherMultiple_returnsNull() throws Exception {
        assertNull(processor.process(record(20, "Customer 20")));
    }

    @Test
    void process_blankName_throwsIllegalArgumentException() {
        SampleRecord r = record(3, "   ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(r));
        assertTrue(ex.getMessage().contains("3"));
        assertTrue(ex.getMessage().contains("blank name"));
    }

    @Test
    void process_nullName_throwsIllegalArgumentException() {
        SampleRecord r = record(5, null);
        assertThrows(IllegalArgumentException.class, () -> processor.process(r));
    }

    @Test
    void process_validRecord_setsStatusToProcessed() throws Exception {
        SampleRecord r = record(7, "Alice");
        SampleRecord result = processor.process(r);
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
    }

    @Test
    void process_validRecord_returnsSameInstance() throws Exception {
        SampleRecord r = record(9, "Bob");
        assertSame(r, processor.process(r));
    }

    @Test
    void process_id1_notFiltered_notRejected() throws Exception {
        SampleRecord result = processor.process(record(1, "Customer 1"));
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
    }
}
