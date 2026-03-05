package com.example.batch.dbtofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CustomerReportProcessorTest {

    private CustomerReportProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new CustomerReportProcessor();
    }

    private CustomerReportRecord record(int totalOrders, String name, BigDecimal amount) {
        CustomerReportRecord r = new CustomerReportRecord();
        r.setCustomerId("CUST-001");
        r.setCustomerName(name);
        r.setRegion("NORTH");
        r.setTotalOrders(totalOrders);
        r.setTotalAmount(amount);
        r.setReportDate(LocalDate.of(2026, 1, 1));
        return r;
    }

    // ---------------------------------------------------------------
    // Filter branch: totalOrders == 0
    // ---------------------------------------------------------------

    @Test
    void process_zeroOrders_returnsNullFiltered() {
        assertNull(processor.process(record(0, "Test Customer", new BigDecimal("100.00"))));
    }

    // ---------------------------------------------------------------
    // Name title-casing
    // ---------------------------------------------------------------

    @Test
    void process_nameAllUpperCase_convertedToTitleCase() {
        CustomerReportRecord result = processor.process(record(1, "JOHN DOE", new BigDecimal("100.00")));
        assertNotNull(result);
        assertEquals("John Doe", result.getCustomerName());
    }

    @Test
    void process_nameAllLowerCase_convertedToTitleCase() {
        CustomerReportRecord result = processor.process(record(1, "jane smith", new BigDecimal("50.00")));
        assertNotNull(result);
        assertEquals("Jane Smith", result.getCustomerName());
    }

    @Test
    void process_nameSingleWord_titleCased() {
        CustomerReportRecord result = processor.process(record(1, "ALICE", new BigDecimal("200.00")));
        assertNotNull(result);
        assertEquals("Alice", result.getCustomerName());
    }

    @Test
    void process_nameWithExtraSpaces_trimmedAndTitleCased() {
        CustomerReportRecord result = processor.process(record(1, "  bob  jones  ", new BigDecimal("75.00")));
        assertNotNull(result);
        assertEquals("Bob Jones", result.getCustomerName());
    }

    @Test
    void process_nameNull_leftAsNull() {
        CustomerReportRecord result = processor.process(record(1, null, new BigDecimal("50.00")));
        assertNotNull(result);
        assertNull(result.getCustomerName(), "Null name should remain null (titleCase returns null for null)");
    }

    @Test
    void process_nameBlank_leftAsBlank() {
        CustomerReportRecord result = processor.process(record(1, "   ", new BigDecimal("50.00")));
        assertNotNull(result);
        // titleCase returns blank as-is
        assertNotNull(result.getCustomerName());
    }

    // ---------------------------------------------------------------
    // Amount rounding
    // ---------------------------------------------------------------

    @Test
    void process_amountWithManyDecimals_roundedTo2dp() {
        CustomerReportRecord result = processor.process(record(3, "Alice", new BigDecimal("123.456789")));
        assertNotNull(result);
        assertEquals(new BigDecimal("123.46"), result.getTotalAmount());
    }

    @Test
    void process_amountIsNull_doesNotThrow() {
        CustomerReportRecord r = record(2, "Bob", null);
        CustomerReportRecord result = processor.process(r);
        assertNotNull(result);
        assertNull(result.getTotalAmount(), "Null amount should remain null (no NPE)");
    }

    @Test
    void process_validRecord_returnsSameInstance() {
        CustomerReportRecord r = record(5, "Carol", new BigDecimal("999.99"));
        assertSame(r, processor.process(r));
    }

    @Test
    void process_positiveOrders_notFiltered() {
        CustomerReportRecord result = processor.process(record(1, "Test", new BigDecimal("10.00")));
        assertNotNull(result);
    }
}
