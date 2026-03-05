package com.example.batch.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceItemProcessorTest {

    private InvoiceItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new InvoiceItemProcessor();
    }

    private InvoiceRecord invoice(long id, BigDecimal amount) {
        return new InvoiceRecord(id, "CUST-001", amount, LocalDate.now(), "PENDING");
    }

    @Test
    void process_nullAmount_throwsIllegalArgumentException() {
        InvoiceRecord inv = invoice(1L, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(inv));
        assertTrue(ex.getMessage().contains("invalid amount"));
        assertTrue(ex.getMessage().contains("1"));
    }

    @Test
    void process_zeroAmount_throwsIllegalArgumentException() {
        InvoiceRecord inv = invoice(2L, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> processor.process(inv));
    }

    @Test
    void process_negativeAmount_throwsIllegalArgumentException() {
        InvoiceRecord inv = invoice(3L, new BigDecimal("-50.00"));
        assertThrows(IllegalArgumentException.class, () -> processor.process(inv));
    }

    @Test
    void process_amountExactly1000_isProcessed() throws Exception {
        InvoiceRecord inv = invoice(4L, new BigDecimal("1000.00"));
        InvoiceRecord result = processor.process(inv);
        assertNotNull(result, "Amount == 1000.00 should not be filtered");
        assertEquals("PROCESSED", result.getStatus());
    }

    @Test
    void process_amountAbove1000_returnsNullFiltered() throws Exception {
        InvoiceRecord inv = invoice(5L, new BigDecimal("1000.01"));
        InvoiceRecord result = processor.process(inv);
        assertNull(result, "Amount > 1000.00 should be filtered (returned null)");
    }

    @Test
    void process_amountWellAbove1000_returnsNullFiltered() throws Exception {
        InvoiceRecord inv = invoice(6L, new BigDecimal("9999.99"));
        assertNull(processor.process(inv));
    }

    @Test
    void process_validAmount_setsStatusToProcessed() throws Exception {
        InvoiceRecord inv = invoice(7L, new BigDecimal("500.00"));
        InvoiceRecord result = processor.process(inv);
        assertNotNull(result);
        assertEquals("PROCESSED", result.getStatus());
        assertEquals(7L, result.getInvoiceId());
    }

    @Test
    void process_validAmount_returnsModifiedSameObject() throws Exception {
        InvoiceRecord inv = invoice(8L, new BigDecimal("250.00"));
        InvoiceRecord result = processor.process(inv);
        assertSame(inv, result, "Processor should return the same instance, mutated");
    }
}
