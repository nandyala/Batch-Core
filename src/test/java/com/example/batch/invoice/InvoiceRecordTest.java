package com.example.batch.invoice;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceRecordTest {

    @Test
    void defaultConstructor_fieldsAreDefault() {
        InvoiceRecord r = new InvoiceRecord();
        assertEquals(0L, r.getInvoiceId());
        assertNull(r.getCustomerId());
        assertNull(r.getAmount());
        assertNull(r.getInvoiceDate());
        assertNull(r.getStatus());
    }

    @Test
    void fullConstructor_allFieldsSet() {
        LocalDate date = LocalDate.of(2026, 1, 15);
        InvoiceRecord r = new InvoiceRecord(1001L, "CUST-0001",
                new BigDecimal("250.00"), date, "PENDING");

        assertEquals(1001L, r.getInvoiceId());
        assertEquals("CUST-0001", r.getCustomerId());
        assertEquals(new BigDecimal("250.00"), r.getAmount());
        assertEquals(date, r.getInvoiceDate());
        assertEquals("PENDING", r.getStatus());
    }

    @Test
    void setters_roundTrip() {
        InvoiceRecord r = new InvoiceRecord();
        LocalDate date = LocalDate.of(2026, 3, 1);
        r.setInvoiceId(42L);
        r.setCustomerId("CUST-9999");
        r.setAmount(new BigDecimal("999.99"));
        r.setInvoiceDate(date);
        r.setStatus("PROCESSED");

        assertEquals(42L, r.getInvoiceId());
        assertEquals("CUST-9999", r.getCustomerId());
        assertEquals(new BigDecimal("999.99"), r.getAmount());
        assertEquals(date, r.getInvoiceDate());
        assertEquals("PROCESSED", r.getStatus());
    }

    @Test
    void toString_containsKeyFields() {
        InvoiceRecord r = new InvoiceRecord(1001L, "CUST-0001",
                new BigDecimal("250.00"), LocalDate.now(), "PENDING");
        String s = r.toString();
        assertTrue(s.contains("1001"));
        assertTrue(s.contains("CUST-0001"));
        assertTrue(s.contains("250.00"));
        assertTrue(s.contains("PENDING"));
    }
}
