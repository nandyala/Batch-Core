package com.example.batch.filetodb;

import org.springframework.batch.infrastructure.item.file.mapping.FieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.FieldSet;
import org.springframework.validation.BindException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Maps a CSV {@link FieldSet} row to a {@link TransactionRecord}.
 *
 * <p>Column names (set in job XML {@code DelimitedLineTokenizer.names}):
 * <pre>
 *   transaction_id, account_number, amount,
 *   transaction_type, transaction_date, description
 * </pre>
 *
 * <p>Date format: {@code yyyy-MM-dd}.
 * Throws {@link IllegalArgumentException} on parse errors — these are treated
 * as PERMANENT skippable exceptions by the step.
 */
public class TransactionFieldSetMapper implements FieldSetMapper<TransactionRecord> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public TransactionRecord mapFieldSet(FieldSet fs) throws BindException {
        TransactionRecord record = new TransactionRecord();

        record.setTransactionId  (required(fs, "transaction_id"));
        record.setAccountNumber  (required(fs, "account_number"));
        record.setTransactionType(required(fs, "transaction_type").toUpperCase());
        record.setDescription    (fs.readString("description"));

        String rawAmount = required(fs, "amount");
        try {
            record.setAmount(new BigDecimal(rawAmount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid amount '" + rawAmount + "' for transaction_id=" + fs.readString("transaction_id"));
        }

        String rawDate = required(fs, "transaction_date");
        try {
            record.setTransactionDate(LocalDate.parse(rawDate, DATE_FMT));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Invalid transaction_date '" + rawDate + "' — expected yyyy-MM-dd");
        }

        return record;
    }

    private static String required(FieldSet fs, String name) {
        String val = fs.readString(name);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }
        return val.trim();
    }
}
