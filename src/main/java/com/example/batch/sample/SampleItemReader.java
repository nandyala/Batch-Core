package com.example.batch.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory stub reader for the sample job.
 *
 * <p>Replace with a real {@code JdbcCursorItemReader} or
 * {@code JdbcPagingItemReader} in production — see the commented
 * XML in {@code sample-job.xml} for a ready-to-use template.
 *
 * <p>Bean declared as {@code scope="step"} so a fresh instance is
 * created for each step execution.
 */
public class SampleItemReader implements ItemReader<SampleRecord> {

    private static final Logger log = LoggerFactory.getLogger(SampleItemReader.class);

    private final List<SampleRecord> data;
    private int index = 0;

    public SampleItemReader() {
        data = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            data.add(new SampleRecord(i, "Customer " + i,
                    "customer" + i + "@example.com", "PENDING"));
        }
        log.info("SampleItemReader initialised with {} records.", data.size());
    }

    @Override
    public SampleRecord read()
            throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (index < data.size()) {
            SampleRecord record = data.get(index++);
            log.debug("Reading record: {}", record);
            return record;
        }
        return null; // signals end of data to Spring Batch
    }
}
