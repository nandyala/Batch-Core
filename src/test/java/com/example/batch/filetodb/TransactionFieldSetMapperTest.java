package com.example.batch.filetodb;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.transform.DefaultFieldSet;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class TransactionFieldSetMapperTest {

    private TransactionFieldSetMapper mapper;

    private static final String[] NAMES = {
        "transaction_id", "account_number", "amount",
        "transaction_type", "transaction_date", "description"
    };

    @BeforeEach
    void setUp() {
        mapper = new TransactionFieldSetMapper();
    }

    private FieldSet fieldSet(String txnId, String account, String amount,
                              String type, String date, String desc) {
        return new DefaultFieldSet(
                new String[]{txnId, account, amount, type, date, desc}, NAMES);
    }

    // ---------------------------------------------------------------
    // Happy path
    // ---------------------------------------------------------------

    @Test
    void mapFieldSet_validRow_mapsAllFields() throws Exception {
        FieldSet fs = fieldSet("TXN001", "ACCT12345678", "250.50",
                "CREDIT", "2026-01-15", "Test payment");

        TransactionRecord record = mapper.mapFieldSet(fs);

        assertEquals("TXN001", record.getTransactionId());
        assertEquals("ACCT12345678", record.getAccountNumber());
        assertEquals(new BigDecimal("250.50"), record.getAmount());
        assertEquals("CREDIT", record.getTransactionType());
        assertEquals(LocalDate.of(2026, 1, 15), record.getTransactionDate());
        assertEquals("Test payment", record.getDescription());
    }

    @Test
    void mapFieldSet_lowercaseType_uppercased() throws Exception {
        FieldSet fs = fieldSet("TXN002", "ACCT12345678", "100.00",
                "debit", "2026-02-01", "");

        TransactionRecord record = mapper.mapFieldSet(fs);
        assertEquals("DEBIT", record.getTransactionType());
    }

    @Test
    void mapFieldSet_emptyDescription_allowed() throws Exception {
        FieldSet fs = fieldSet("TXN003", "ACCT12345678", "50.00",
                "CREDIT", "2026-03-01", "");
        // Empty description is optional — should not throw
        assertDoesNotThrow(() -> mapper.mapFieldSet(fs));
    }

    // ---------------------------------------------------------------
    // Missing required fields
    // ---------------------------------------------------------------

    @Test
    void mapFieldSet_blankTransactionId_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("", "ACCT12345678", "100.00", "CREDIT", "2026-01-15", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("transaction_id"));
    }

    @Test
    void mapFieldSet_blankAccountNumber_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN004", "", "100.00", "CREDIT", "2026-01-15", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("account_number"));
    }

    @Test
    void mapFieldSet_blankTransactionType_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN005", "ACCT12345678", "100.00", "", "2026-01-15", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("transaction_type"));
    }

    @Test
    void mapFieldSet_blankAmount_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN006", "ACCT12345678", "", "CREDIT", "2026-01-15", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("amount"));
    }

    @Test
    void mapFieldSet_blankDate_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN007", "ACCT12345678", "100.00", "CREDIT", "", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("transaction_date"));
    }

    // ---------------------------------------------------------------
    // Invalid format fields
    // ---------------------------------------------------------------

    @Test
    void mapFieldSet_invalidAmount_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN008", "ACCT12345678", "not-a-number", "CREDIT", "2026-01-15", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("Invalid amount"));
        assertTrue(ex.getMessage().contains("not-a-number"));
    }

    @Test
    void mapFieldSet_invalidDate_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN009", "ACCT12345678", "100.00", "CREDIT", "15-01-2026", "");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> mapper.mapFieldSet(fs));
        assertTrue(ex.getMessage().contains("Invalid transaction_date"));
        assertTrue(ex.getMessage().contains("15-01-2026"));
    }

    @Test
    void mapFieldSet_dateWrongFormat_throwsIllegalArgumentException() {
        FieldSet fs = fieldSet("TXN010", "ACCT12345678", "100.00", "CREDIT", "2026/01/15", "");
        assertThrows(IllegalArgumentException.class, () -> mapper.mapFieldSet(fs));
    }
}
