package com.example.batch.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemReader;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub in-memory reader for the invoice job.
 *
 * Replace with a real {@code JdbcCursorItemReader} pointing at your INVOICE table:
 * <pre>
 *   &lt;bean id="invoiceReader"
 *         class="org.springframework.batch.item.database.JdbcCursorItemReader"
 *         scope="step"&gt;
 *     &lt;property name="dataSource" ref="dataSource"/&gt;
 *     &lt;property name="sql"
 *               value="SELECT invoice_id, customer_id, amount, invoice_date, status
 *                        FROM   INVOICE
 *                        WHERE  status = 'PENDING'
 *                        ORDER  BY invoice_id"/&gt;
 *     &lt;property name="rowMapper"&gt;
 *       &lt;bean class="com.example.batch.invoice.InvoiceRowMapper"/&gt;
 *     &lt;/property&gt;
 *   &lt;/bean&gt;
 * </pre>
 */
public class InvoiceItemReader implements ItemReader<InvoiceRecord> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceItemReader.class);

    private final List<InvoiceRecord> data;
    private int index = 0;

    public InvoiceItemReader() {
        data = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= 15; i++) {
            data.add(new InvoiceRecord(
                    1000L + i,
                    "CUST-" + String.format("%04d", i),
                    new BigDecimal("100.00").multiply(new BigDecimal(i)),
                    today.minusDays(i),
                    "PENDING"));
        }
        log.info("InvoiceItemReader initialised with {} records.", data.size());
    }

    @Override
    public InvoiceRecord read() throws Exception {
        if (index < data.size()) {
            InvoiceRecord record = data.get(index++);
            log.debug("Reading: {}", record);
            return record;
        }
        return null; // signals end of data to Spring Batch
    }
}
