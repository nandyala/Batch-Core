package com.example.batch.dbtofile;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class CustomerReportHeaderCallbackTest {

    @Test
    void writeHeader_writesCorrectCsvHeader() throws Exception {
        CustomerReportHeaderCallback callback = new CustomerReportHeaderCallback();
        StringWriter writer = new StringWriter();

        callback.writeHeader(writer);

        assertEquals(
            "customer_id,customer_name,region,total_orders,total_amount,report_date",
            writer.toString()
        );
    }

    @Test
    void writeHeader_containsAllExpectedColumns() throws Exception {
        CustomerReportHeaderCallback callback = new CustomerReportHeaderCallback();
        StringWriter writer = new StringWriter();
        callback.writeHeader(writer);
        String header = writer.toString();

        assertTrue(header.contains("customer_id"));
        assertTrue(header.contains("customer_name"));
        assertTrue(header.contains("region"));
        assertTrue(header.contains("total_orders"));
        assertTrue(header.contains("total_amount"));
        assertTrue(header.contains("report_date"));
    }
}
