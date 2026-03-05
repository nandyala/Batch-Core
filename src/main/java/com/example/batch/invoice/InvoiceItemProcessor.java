package com.example.batch.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.math.BigDecimal;

/**
 * Validates and transforms an {@link InvoiceRecord} before writing.
 *
 * <p>Returns {@code null} to filter (skip without error) records that
 * fail business validation — Spring Batch counts these as filtered items.
 *
 * Replace with your real business logic.
 */
public class InvoiceItemProcessor implements ItemProcessor<InvoiceRecord, InvoiceRecord> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceItemProcessor.class);

    @Override
    public InvoiceRecord process(InvoiceRecord invoice) throws Exception {
        log.debug("Processing: {}", invoice);

        // Validation: reject zero/negative amounts
        if (invoice.getAmount() == null || invoice.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Invoice " + invoice.getInvoiceId() + " has invalid amount: " + invoice.getAmount());
        }

        // Business rule: filter invoices above $1000 (example — adjust as needed)
        if (invoice.getAmount().compareTo(new BigDecimal("1000.00")) > 0) {
            log.info("Filtering invoice {} — amount {} exceeds threshold (requires manual review).",
                     invoice.getInvoiceId(), invoice.getAmount());
            return null; // filtered, not written, not an error
        }

        // Transform: mark as processed
        invoice.setStatus("PROCESSED");
        return invoice;
    }
}
