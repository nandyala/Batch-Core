package com.example.batch.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

/**
 * Sample processor: validates and transforms a {@link SampleRecord}.
 *
 * <p>Returns {@code null} to filter (skip without error) records
 * that fail business validation — Spring Batch counts these as
 * filtered items, incrementing the filter count.
 *
 * <p>Replace with your own business logic.
 */
public class SampleItemProcessor implements ItemProcessor<SampleRecord, SampleRecord> {

    private static final Logger log = LoggerFactory.getLogger(SampleItemProcessor.class);

    @Override
    public SampleRecord process(SampleRecord record) throws Exception {
        log.debug("Processing: {}", record);

        // Example business rule: filter out every 10th record
        if (record.getId() % 10 == 0) {
            log.info("Filtering record id={} (business rule: divisible by 10)", record.getId());
            return null; // filtered — not written, not an error
        }

        // Example validation: reject records with blank names
        if (record.getName() == null || record.getName().isBlank()) {
            throw new IllegalArgumentException(
                "Record id=" + record.getId() + " has a blank name — skipping.");
        }

        // Transform: mark as processed
        record.setStatus("PROCESSED");
        return record;
    }
}
