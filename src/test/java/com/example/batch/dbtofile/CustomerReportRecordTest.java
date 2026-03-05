package com.example.batch.dbtofile;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CustomerReportRecordTest {

    @Test
    void defaultConstructor_allFieldsAreNull() {
        CustomerReportRecord r = new CustomerReportRecord();
        assertNull(r.getCustomerId());
        assertNull(r.getCustomerName());
        assertNull(r.getRegion());
        assertEquals(0, r.getTotalOrders());
        assertNull(r.getTotalAmount());
        assertNull(r.getReportDate());
    }

    @Test
    void setters_roundTrip_strings() {
        CustomerReportRecord r = new CustomerReportRecord();
        r.setCustomerId("CUST-001");
        r.setCustomerName("John Doe");
        r.setRegion("NORTH");

        assertEquals("CUST-001", r.getCustomerId());
        assertEquals("John Doe", r.getCustomerName());
        assertEquals("NORTH", r.getRegion());
    }

    @Test
    void setters_roundTrip_numeric() {
        CustomerReportRecord r = new CustomerReportRecord();
        r.setTotalOrders(42);
        r.setTotalAmount(new BigDecimal("1234.56"));

        assertEquals(42, r.getTotalOrders());
        assertEquals(new BigDecimal("1234.56"), r.getTotalAmount());
    }

    @Test
    void setters_roundTrip_date() {
        CustomerReportRecord r = new CustomerReportRecord();
        LocalDate date = LocalDate.of(2026, 1, 15);
        r.setReportDate(date);
        assertEquals(date, r.getReportDate());
    }

    @Test
    void setters_acceptNull() {
        CustomerReportRecord r = new CustomerReportRecord();
        r.setCustomerId(null);
        r.setCustomerName(null);
        r.setTotalAmount(null);
        r.setReportDate(null);

        assertNull(r.getCustomerId());
        assertNull(r.getCustomerName());
        assertNull(r.getTotalAmount());
        assertNull(r.getReportDate());
    }
}
