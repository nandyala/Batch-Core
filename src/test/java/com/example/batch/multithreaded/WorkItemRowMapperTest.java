package com.example.batch.multithreaded;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemRowMapperTest {

    @Mock
    private ResultSet rs;

    private WorkItemRowMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WorkItemRowMapper();
    }

    @Test
    void mapRow_allColumnsPresent_mapsCorrectly() throws Exception {
        LocalDateTime created   = LocalDateTime.of(2026, 1, 15, 10, 30, 0);
        LocalDateTime processed = LocalDateTime.of(2026, 1, 15, 10, 31, 0);

        when(rs.getLong("id")).thenReturn(101L);
        when(rs.getString("payload")).thenReturn("process me");
        when(rs.getInt("priority")).thenReturn(3);
        when(rs.getString("status")).thenReturn("PENDING");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(created));
        when(rs.getTimestamp("processed_at")).thenReturn(Timestamp.valueOf(processed));

        WorkItem item = mapper.mapRow(rs, 1);

        assertNotNull(item);
        assertEquals(101L, item.getId());
        assertEquals("process me", item.getPayload());
        assertEquals(3, item.getPriority());
        assertEquals("PENDING", item.getStatus());
        assertEquals(created, item.getCreatedAt());
        assertEquals(processed, item.getProcessedAt());
    }

    @Test
    void mapRow_nullCreatedAt_leftAsNull() throws Exception {
        when(rs.getLong("id")).thenReturn(102L);
        when(rs.getString("payload")).thenReturn("payload");
        when(rs.getInt("priority")).thenReturn(0);
        when(rs.getString("status")).thenReturn("PENDING");
        when(rs.getTimestamp("created_at")).thenReturn(null);
        when(rs.getTimestamp("processed_at")).thenReturn(null);

        WorkItem item = mapper.mapRow(rs, 2);

        assertNotNull(item);
        assertNull(item.getCreatedAt(), "Null created_at timestamp should leave createdAt null");
        assertNull(item.getProcessedAt(), "Null processed_at timestamp should leave processedAt null");
    }

    @Test
    void mapRow_nullProcessedAtOnly_processedAtIsNull() throws Exception {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 8, 0, 0);

        when(rs.getLong("id")).thenReturn(103L);
        when(rs.getString("payload")).thenReturn("work");
        when(rs.getInt("priority")).thenReturn(1);
        when(rs.getString("status")).thenReturn("PENDING");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(created));
        when(rs.getTimestamp("processed_at")).thenReturn(null);

        WorkItem item = mapper.mapRow(rs, 3);

        assertNotNull(item);
        assertEquals(created, item.getCreatedAt());
        assertNull(item.getProcessedAt());
    }
}
