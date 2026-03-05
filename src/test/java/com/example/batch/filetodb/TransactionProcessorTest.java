package com.example.batch.filetodb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionProcessorTest {

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor();
    }

    private TransactionRecord record(String type, BigDecimal amount, String account) {
        TransactionRecord r = new TransactionRecord();
        r.setTransactionId("TXN001");
        r.setTransactionType(type);
        r.setAmount(amount);
        r.setAccountNumber(account);
        r.setTransactionDate(LocalDate.of(2026, 1, 15));
        return r;
    }

    // ---------------------------------------------------------------
    // Transaction type validation
    // ---------------------------------------------------------------

    @Test
    void process_validCredit_passes() {
        TransactionRecord result = processor.process(
                record("CREDIT", new BigDecimal("100.00"), "ACCT12345678"));
        assertNotNull(result);
    }

    @Test
    void process_validDebit_passes() {
        TransactionRecord result = processor.process(
                record("DEBIT", new BigDecimal("50.00"), "ACCT12345678"));
        assertNotNull(result);
    }

    @Test
    void process_invalidType_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("TRANSFER", new BigDecimal("100.00"), "ACCT12345678")));
        assertTrue(ex.getMessage().contains("TRANSFER"));
        assertTrue(ex.getMessage().contains("TXN001"));
    }

    @Test
    void process_nullType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(record(null, new BigDecimal("100.00"), "ACCT12345678")));
    }

    // ---------------------------------------------------------------
    // Amount validation
    // ---------------------------------------------------------------

    @Test
    void process_nullAmount_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", null, "ACCT12345678")));
        assertTrue(ex.getMessage().contains("Amount must be positive"));
    }

    @Test
    void process_zeroAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", BigDecimal.ZERO, "ACCT12345678")));
    }

    @Test
    void process_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", new BigDecimal("-10.00"), "ACCT12345678")));
    }

    // ---------------------------------------------------------------
    // Account number validation
    // ---------------------------------------------------------------

    @Test
    void process_accountTooShort_throwsIllegalArgumentException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", new BigDecimal("100.00"), "SHORT")));
        assertTrue(ex.getMessage().contains("SHORT"));
        assertTrue(ex.getMessage().contains("8–16"));
    }

    @Test
    void process_accountTooLong_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", new BigDecimal("100.00"), "ACCT12345678901234")));
    }

    @Test
    void process_accountWithSpecialChars_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> processor.process(record("CREDIT", new BigDecimal("100.00"), "ACCT-1234567")));
    }

    @Test
    void process_accountExactly8Chars_passes() {
        assertNotNull(processor.process(record("CREDIT", new BigDecimal("100.00"), "ACCT1234")));
    }

    @Test
    void process_accountExactly16Chars_passes() {
        assertNotNull(processor.process(record("CREDIT", new BigDecimal("100.00"), "ACCT123456789012")));
    }

    // ---------------------------------------------------------------
    // Amount rounding / filter
    // ---------------------------------------------------------------

    @Test
    void process_amountRoundedTo2dp() {
        TransactionRecord result = processor.process(
                record("CREDIT", new BigDecimal("100.456"), "ACCT12345678"));
        assertNotNull(result);
        assertEquals(new BigDecimal("100.46"), result.getAmount());
    }

    @Test
    void process_amountRoundsToZero_returnsNullFiltered() {
        // 0.004 rounds to 0.00 with HALF_UP at 2dp
        TransactionRecord result = processor.process(
                record("CREDIT", new BigDecimal("0.004"), "ACCT12345678"));
        assertNull(result, "Amount that rounds to 0.00 should be filtered (null)");
    }

    @Test
    void process_validRecord_returnsSameInstance() {
        TransactionRecord r = record("DEBIT", new BigDecimal("250.00"), "ACCT12345678");
        assertSame(r, processor.process(r));
    }
}
