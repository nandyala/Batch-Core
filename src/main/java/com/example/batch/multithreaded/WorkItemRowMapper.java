package com.example.batch.multithreaded;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Maps a {@code WORK_ITEM} result set row to {@link WorkItem}.
 */
public class WorkItemRowMapper implements RowMapper<WorkItem> {

    @Override
    public WorkItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        WorkItem item = new WorkItem();
        item.setId      (rs.getLong  ("id"));
        item.setPayload (rs.getString("payload"));
        item.setPriority(rs.getInt   ("priority"));
        item.setStatus  (rs.getString("status"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp processedAt = rs.getTimestamp("processed_at");
        if (processedAt != null) item.setProcessedAt(processedAt.toLocalDateTime());

        return item;
    }
}
