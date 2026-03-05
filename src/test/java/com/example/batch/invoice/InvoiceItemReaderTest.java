package com.example.batch.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceItemReaderTest {

    private InvoiceItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new InvoiceItemReader();
    }

    @Test
    void read_firstRecord_isInvoice1001() throws Exception {
        InvoiceRecord first = reader.read();
        assertNotNull(first);
        assertEquals(1001L, first.getInvoiceId());
        assertEquals("CUST-0001", first.getCustomerId());
        assertEquals("PENDING", first.getStatus());
    }

    @Test
    void read_allFifteenRecords_returnsNonNull() throws Exception {
        for (int i = 0; i < 15; i++) {
            assertNotNull(reader.read(), "Expected non-null record at index " + i);
        }
    }

    @Test
    void read_afterAllRecordsExhausted_returnsNull() throws Exception {
        for (int i = 0; i < 15; i++) {
            reader.read();
        }
        assertNull(reader.read(), "Expected null after all 15 records consumed");
    }

    @Test
    void read_lastRecord_isInvoice1015() throws Exception {
        InvoiceRecord last = null;
        for (int i = 0; i < 15; i++) {
            last = reader.read();
        }
        assertNotNull(last);
        assertEquals(1015L, last.getInvoiceId());
        assertEquals("CUST-0015", last.getCustomerId());
    }

    @Test
    void read_amountsAreMultiplesOf100() throws Exception {
        for (int i = 1; i <= 15; i++) {
            InvoiceRecord r = reader.read();
            assertNotNull(r);
            // Amount = 100.00 * i
            assertEquals(0, r.getAmount().compareTo(
                    new java.math.BigDecimal("100.00").multiply(new java.math.BigDecimal(i))),
                    "Amount for record " + i + " should be " + (100 * i));
        }
    }
}
