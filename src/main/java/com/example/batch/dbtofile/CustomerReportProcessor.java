package com.example.batch.dbtofile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;

import java.math.RoundingMode;

/**
 * Processes a {@link CustomerReportRecord} before it is written to the CSV file.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Normalise customer name (trim, title-case).</li>
 *   <li>Round {@code totalAmount} to 2 decimal places.</li>
 *   <li>Return {@code null} for records with {@code totalOrders == 0} so they are filtered out.</li>
 * </ul>
 *
 * <p>Returning {@code null} increments Spring Batch's filter count for the step.
 */
public class CustomerReportProcessor implements ItemProcessor<CustomerReportRecord, CustomerReportRecord> {

    private static final Logger log = LoggerFactory.getLogger(CustomerReportProcessor.class);

    @Override
    public CustomerReportRecord process(CustomerReportRecord record) {
        // Filter: skip customers with no orders
        if (record.getTotalOrders() == 0) {
            log.debug("Filtering customer '{}' — no orders in period", record.getCustomerId());
            return null;
        }

        // Normalise name
        record.setCustomerName(titleCase(record.getCustomerName()));

        // Round amount to exactly 2 dp for CSV consistency
        if (record.getTotalAmount() != null) {
            record.setTotalAmount(
                record.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
            );
        }

        return record;
    }

    private static String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
