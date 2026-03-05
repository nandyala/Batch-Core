package com.example.batch.invoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class InvoiceItemWriterTest {

    private InvoiceItemWriter writer;

    @BeforeEach
    void setUp() {
        writer = new InvoiceItemWriter();
    }

    private InvoiceRecord invoice(long id) {
        return new InvoiceRecord(id, "CUST-" + id,
                new BigDecimal("100.00"), LocalDate.now(), "PROCESSED");
    }

    @Test
    void write_emptyChunk_doesNotThrow() {
        assertDoesNotThrow(() -> writer.write(new Chunk<>(List.of())));
    }

    @Test
    void write_singleItem_doesNotThrow() {
        assertDoesNotThrow(() -> writer.write(new Chunk<>(List.of(invoice(1001L)))));
    }

    @Test
    void write_multipleItems_doesNotThrow() {
        assertDoesNotThrow(() -> writer.write(new Chunk<>(
                List.of(invoice(1001L), invoice(1002L), invoice(1003L)))));
    }
}
