package com.example.batch.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SampleRecordTest {

    @Test
    void defaultConstructor_fieldsAreDefault() {
        SampleRecord r = new SampleRecord();
        assertEquals(0, r.getId());
        assertNull(r.getName());
        assertNull(r.getEmail());
        assertNull(r.getStatus());
    }

    @Test
    void fullConstructor_allFieldsSet() {
        SampleRecord r = new SampleRecord(5, "Alice", "alice@example.com", "PENDING");
        assertEquals(5, r.getId());
        assertEquals("Alice", r.getName());
        assertEquals("alice@example.com", r.getEmail());
        assertEquals("PENDING", r.getStatus());
    }

    @Test
    void setters_roundTrip() {
        SampleRecord r = new SampleRecord();
        r.setId(99);
        r.setName("Bob");
        r.setEmail("bob@example.com");
        r.setStatus("PROCESSED");

        assertEquals(99, r.getId());
        assertEquals("Bob", r.getName());
        assertEquals("bob@example.com", r.getEmail());
        assertEquals("PROCESSED", r.getStatus());
    }

    @Test
    void toString_containsIdNameStatus() {
        SampleRecord r = new SampleRecord(7, "Charlie", "c@example.com", "PENDING");
        String s = r.toString();
        assertTrue(s.contains("7"));
        assertTrue(s.contains("Charlie"));
        assertTrue(s.contains("PENDING"));
    }
}
