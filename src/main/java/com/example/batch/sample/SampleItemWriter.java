package com.example.batch.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/**
 * Stub writer that logs each processed record.
 *
 * <p>Replace with a real {@code JdbcBatchItemWriter} in production — see
 * the commented XML in {@code sample-job.xml} for a ready-to-use template.
 */
public class SampleItemWriter implements ItemWriter<SampleRecord> {

    private static final Logger log = LoggerFactory.getLogger(SampleItemWriter.class);

    @Override
    public void write(Chunk<? extends SampleRecord> chunk) throws Exception {
        log.info("Writing chunk of {} records.", chunk.size());
        for (SampleRecord record : chunk) {
            log.debug("  Writing: {}", record);
            // TODO: replace with real persistence logic
            //   e.g. jdbcBatchItemWriter.write(chunk)
        }
    }
}
