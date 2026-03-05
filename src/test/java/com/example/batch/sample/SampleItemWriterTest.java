package com.example.batch.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SampleItemWriterTest {

    private SampleItemWriter writer;

    @BeforeEach
    void setUp() {
        writer = new SampleItemWriter();
    }

    @Test
    void write_emptyChunk_doesNotThrow() {
        assertDoesNotThrow(() -> writer.write(new Chunk<>(List.of())));
    }

    @Test
    void write_singleItem_doesNotThrow() {
        SampleRecord r = new SampleRecord(1, "Alice", "alice@example.com", "PROCESSED");
        assertDoesNotThrow(() -> writer.write(new Chunk<>(List.of(r))));
    }

    @Test
    void write_multipleItems_doesNotThrow() {
        List<SampleRecord> items = List.of(
                new SampleRecord(1, "Alice", "alice@example.com", "PROCESSED"),
                new SampleRecord(2, "Bob",   "bob@example.com",   "PROCESSED"),
                new SampleRecord(3, "Carol", "carol@example.com", "PROCESSED")
        );
        assertDoesNotThrow(() -> writer.write(new Chunk<>(items)));
    }
}
