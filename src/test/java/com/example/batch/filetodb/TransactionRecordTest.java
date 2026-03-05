package com.example.batch.filetodb;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionRecordTest {

    @Test
    void defaultConstructor_allFieldsAreNull() {
        TransactionRecord r = new TransactionRecord();
        assertNull(r.getTransactionId());
        assertNull(r.getAccountNumber());
        assertNull(r.getAmount());
        assertNull(r.getTransactionType());
        assertNull(r.getTransactionDate());
        assertNull(r.getDescription());
    }

    @Test
    void setters_roundTrip_strings() {
        TransactionRecord r = new TransactionRecord();
        r.setTransactionId("TXN001");
        r.setAccountNumber("ACCT12345678");
        r.setTransactionType("CREDIT");
        r.setDescription("Payment for invoice");

        assertEquals("TXN001", r.getTransactionId());
        assertEquals("ACCT12345678", r.getAccountNumber());
        assertEquals("CREDIT", r.getTransactionType());
        assertEquals("Payment for invoice", r.getDescription());
    }

    @Test
    void setters_roundTrip_amountAndDate() {
        TransactionRecord r = new TransactionRecord();
        r.setAmount(new BigDecimal("500.75"));
        r.setTransactionDate(LocalDate.of(2026, 1, 15));

        assertEquals(new BigDecimal("500.75"), r.getAmount());
        assertEquals(LocalDate.of(2026, 1, 15), r.getTransactionDate());
    }

    @Test
    void setters_acceptNullOptionalField() {
        TransactionRecord r = new TransactionRecord();
        r.setDescription(null);
        assertNull(r.getDescription());
    }
}
