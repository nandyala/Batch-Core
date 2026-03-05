package com.example.batch.filetodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Validates and normalises a {@link TransactionRecord} before it is written to the database.
 *
 * <p>Validation rules (throw {@link IllegalArgumentException} → PERMANENT skip):
 * <ul>
 *   <li>Amount must be positive.</li>
 *   <li>Transaction type must be CREDIT or DEBIT.</li>
 *   <li>Account number must match format: 8–16 alphanumeric characters.</li>
 * </ul>
 *
 * <p>Returns {@code null} (filter) if the record's amount rounds to zero after scaling.
 */
public class TransactionProcessor implements ItemProcessor<TransactionRecord, TransactionRecord> {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    @Override
    public TransactionRecord process(TransactionRecord record) {
        // Validate transaction type
        if (!"CREDIT".equals(record.getTransactionType()) && !"DEBIT".equals(record.getTransactionType())) {
            throw new IllegalArgumentException(
                "Invalid transaction_type '" + record.getTransactionType()
                + "' for transaction_id=" + record.getTransactionId()
                + ". Must be CREDIT or DEBIT.");
        }

        // Validate amount
        if (record.getAmount() == null || record.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Amount must be positive for transaction_id=" + record.getTransactionId());
        }

        // Validate account number format (8–16 alphanumeric)
        if (!record.getAccountNumber().matches("[A-Za-z0-9]{8,16}")) {
            throw new IllegalArgumentException(
                "Invalid account_number '" + record.getAccountNumber()
                + "' for transaction_id=" + record.getTransactionId()
                + ". Must be 8–16 alphanumeric characters.");
        }

        // Scale amount to 2 dp
        BigDecimal scaled = record.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) == 0) {
            log.debug("Filtering zero-amount transaction: {}", record.getTransactionId());
            return null;  // filter — increments Spring Batch filter count
        }
        record.setAmount(scaled);

        return record;
    }
}
