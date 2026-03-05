package com.example.batch.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SampleItemReaderTest {

    private SampleItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new SampleItemReader();
    }

    @Test
    void read_firstRecord_hasId1() throws Exception {
        SampleRecord first = reader.read();
        assertNotNull(first);
        assertEquals(1, first.getId());
        assertEquals("Customer 1", first.getName());
        assertEquals("customer1@example.com", first.getEmail());
        assertEquals("PENDING", first.getStatus());
    }

    @Test
    void read_allTenRecords_returnNonNull() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertNotNull(reader.read(), "Expected non-null at index " + i);
        }
    }

    @Test
    void read_afterAllTenRecords_returnsNull() throws Exception {
        for (int i = 0; i < 10; i++) {
            reader.read();
        }
        assertNull(reader.read(), "Expected null after exhausting all 10 records");
    }

    @Test
    void read_lastRecord_hasId10() throws Exception {
        SampleRecord last = null;
        for (int i = 0; i < 10; i++) {
            last = reader.read();
        }
        assertNotNull(last);
        assertEquals(10, last.getId());
        assertEquals("Customer 10", last.getName());
    }

    @Test
    void read_consecutiveCalls_incrementsId() throws Exception {
        SampleRecord r1 = reader.read();
        SampleRecord r2 = reader.read();
        assertNotNull(r1);
        assertNotNull(r2);
        assertEquals(r1.getId() + 1, r2.getId());
    }
}
