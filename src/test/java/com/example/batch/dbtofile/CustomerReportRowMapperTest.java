package com.example.batch.dbtofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerReportRowMapperTest {

    @Mock
    private ResultSet rs;

    private CustomerReportRowMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new CustomerReportRowMapper();
    }

    @Test
    void mapRow_allColumnsPresent_mapsCorrectly() throws Exception {
        when(rs.getString("customer_id")).thenReturn("CUST-001");
        when(rs.getString("customer_name")).thenReturn("John Doe");
        when(rs.getString("region")).thenReturn("NORTH");
        when(rs.getInt("total_orders")).thenReturn(5);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("1234.56"));
        when(rs.getDate("report_date")).thenReturn(Date.valueOf(LocalDate.of(2026, 1, 15)));

        CustomerReportRecord record = mapper.mapRow(rs, 1);

        assertNotNull(record);
        assertEquals("CUST-001", record.getCustomerId());
        assertEquals("John Doe", record.getCustomerName());
        assertEquals("NORTH", record.getRegion());
        assertEquals(5, record.getTotalOrders());
        assertEquals(new BigDecimal("1234.56"), record.getTotalAmount());
        assertEquals(LocalDate.of(2026, 1, 15), record.getReportDate());
    }

    @Test
    void mapRow_nullReportDate_reportDateIsNull() throws Exception {
        when(rs.getString("customer_id")).thenReturn("CUST-002");
        when(rs.getString("customer_name")).thenReturn("Jane Smith");
        when(rs.getString("region")).thenReturn("SOUTH");
        when(rs.getInt("total_orders")).thenReturn(3);
        when(rs.getBigDecimal("total_amount")).thenReturn(new BigDecimal("500.00"));
        when(rs.getDate("report_date")).thenReturn(null);

        CustomerReportRecord record = mapper.mapRow(rs, 2);

        assertNotNull(record);
        assertNull(record.getReportDate(), "Null sql.Date should leave reportDate null");
        assertEquals("CUST-002", record.getCustomerId());
    }

    @Test
    void mapRow_zeroOrders_mapsCorrectly() throws Exception {
        when(rs.getString("customer_id")).thenReturn("CUST-003");
        when(rs.getString("customer_name")).thenReturn("No Orders");
        when(rs.getString("region")).thenReturn("EAST");
        when(rs.getInt("total_orders")).thenReturn(0);
        when(rs.getBigDecimal("total_amount")).thenReturn(BigDecimal.ZERO);
        when(rs.getDate("report_date")).thenReturn(null);

        CustomerReportRecord record = mapper.mapRow(rs, 3);

        assertNotNull(record);
        assertEquals(0, record.getTotalOrders());
    }
}
