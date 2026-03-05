package com.example.batch.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CsvHeaderCallback}.
 *
 * <p>Verifies that the header line is written exactly as provided to the constructor.
 */
class CsvHeaderCallbackTest {

    @Test
    void writeHeader_writesExactHeaderString() throws IOException {
        String header = "customer_id,customer_name,region,total_orders,total_amount,report_date";
        CsvHeaderCallback callback = new CsvHeaderCallback(header);

        StringWriter writer = new StringWriter();
        callback.writeHeader(writer);

        assertEquals(header, writer.toString(),
                "Header written to Writer must match the constructor argument exactly");
    }

    @ParameterizedTest(name = "header=\"{0}\"")
    @ValueSource(strings = {
        "id,name,value",
        "HEADER_COL_1|HEADER_COL_2",
        "",                            // empty header (edge case)
        "single_column"
    })
    void writeHeader_variousHeaderFormats_writtenVerbatim(String header) throws IOException {
        CsvHeaderCallback callback = new CsvHeaderCallback(header);

        StringWriter writer = new StringWriter();
        callback.writeHeader(writer);

        assertEquals(header, writer.toString());
    }

    @Test
    void writeHeader_specialCharacters_preserved() throws IOException {
        String header = "col1\tcol2\tcol3";   // tab-separated
        CsvHeaderCallback callback = new CsvHeaderCallback(header);

        StringWriter writer = new StringWriter();
        callback.writeHeader(writer);

        assertEquals(header, writer.toString(), "Tab characters must be preserved verbatim");
    }

    @Test
    void writeHeader_unicodeCharacters_preserved() throws IOException {
        String header = "名前,住所,金額";  // Japanese characters
        CsvHeaderCallback callback = new CsvHeaderCallback(header);

        StringWriter writer = new StringWriter();
        callback.writeHeader(writer);

        assertEquals(header, writer.toString());
    }

    @Test
    void writeHeader_calledMultipleTimes_writesEachTime() throws IOException {
        // FlatFileItemWriter calls writeHeader once, but the callback should be idempotent
        CsvHeaderCallback callback = new CsvHeaderCallback("col1,col2");

        StringWriter w1 = new StringWriter();
        StringWriter w2 = new StringWriter();

        callback.writeHeader(w1);
        callback.writeHeader(w2);

        assertEquals("col1,col2", w1.toString());
        assertEquals("col1,col2", w2.toString());
    }
}
