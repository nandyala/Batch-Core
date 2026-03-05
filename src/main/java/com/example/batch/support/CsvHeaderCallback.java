package com.example.batch.support;

import org.springframework.batch.item.file.FlatFileHeaderCallback;

import java.io.IOException;
import java.io.Writer;

/**
 * Generic {@link FlatFileHeaderCallback} that writes a single, pre-configured
 * header line to a CSV file.
 *
 * <p>Usage in Spring XML:
 * <pre>{@code
 * <bean class="com.example.batch.support.CsvHeaderCallback">
 *     <constructor-arg value="customer_id,customer_name,region,total_orders,total_amount,report_date"/>
 * </bean>
 * }</pre>
 */
public class CsvHeaderCallback implements FlatFileHeaderCallback {

    private final String headerLine;

    public CsvHeaderCallback(String headerLine) {
        this.headerLine = headerLine;
    }

    @Override
    public void writeHeader(Writer writer) throws IOException {
        writer.write(headerLine);
    }
}