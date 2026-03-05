package com.example.batch.dbtofile;

import org.springframework.batch.infrastructure.item.file.FlatFileHeaderCallback;

import java.io.IOException;
import java.io.Writer;

/**
 * Writes the CSV header line for the customer report export file.
 * Column order must match the field names configured on
 * {@code BeanWrapperFieldExtractor} in {@code db-to-file-job.xml}.
 */
public class CustomerReportHeaderCallback implements FlatFileHeaderCallback {

    @Override
    public void writeHeader(Writer writer) throws IOException {
        writer.write("customer_id,customer_name,region,total_orders,total_amount,report_date");
    }
}
