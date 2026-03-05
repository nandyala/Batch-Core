package com.example.batch.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Stub writer that logs each processed invoice.
 *
 * Replace with a real {@code JdbcBatchItemWriter}:
 * <pre>
 *   &lt;bean id="invoiceWriter"
 *         class="org.springframework.batch.item.database.JdbcBatchItemWriter"
 *         scope="step"&gt;
 *     &lt;property name="dataSource" ref="dataSource"/&gt;
 *     &lt;property name="sql"&gt;
 *       &lt;value&gt;
 *         UPDATE INVOICE
 *            SET status = :status,
 *                processed_date = GETDATE()
 *          WHERE invoice_id = :invoiceId
 *       &lt;/value&gt;
 *     &lt;/property&gt;
 *     &lt;property name="itemSqlParameterSourceProvider"&gt;
 *       &lt;bean class="org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider"/&gt;
 *     &lt;/property&gt;
 *   &lt;/bean&gt;
 * </pre>
 */
public class InvoiceItemWriter implements ItemWriter<InvoiceRecord> {

    private static final Logger log = LoggerFactory.getLogger(InvoiceItemWriter.class);

    @Override
    public void write(Chunk<? extends InvoiceRecord> chunk) throws Exception {
        log.info("Writing chunk of {} invoices.", chunk.size());
        for (InvoiceRecord invoice : chunk) {
            log.debug("  Writing: {}", invoice);
            // TODO: replace with real persistence (JdbcBatchItemWriter or repository)
        }
    }
}
