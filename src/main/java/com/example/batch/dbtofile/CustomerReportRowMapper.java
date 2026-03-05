package com.example.batch.dbtofile;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a {@code CUSTOMER_REPORT} result set row to {@link CustomerReportRecord}.
 *
 * <p>Expected columns (matching the SQL in db-to-file-job.xml):
 * <pre>
 *   customer_id, customer_name, region,
 *   total_orders, total_amount, report_date
 * </pre>
 */
public class CustomerReportRowMapper implements RowMapper<CustomerReportRecord> {

    @Override
    public CustomerReportRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        CustomerReportRecord record = new CustomerReportRecord();
        record.setCustomerId  (rs.getString("customer_id"));
        record.setCustomerName(rs.getString("customer_name"));
        record.setRegion      (rs.getString("region"));
        record.setTotalOrders (rs.getInt   ("total_orders"));
        record.setTotalAmount (rs.getBigDecimal("total_amount"));

        java.sql.Date sqlDate = rs.getDate("report_date");
        if (sqlDate != null) {
            record.setReportDate(sqlDate.toLocalDate());
        }
        return record;
    }
}
