package com.example.batch.dbtofile;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents one row from the CUSTOMER_REPORT view on the secondary (reporting) database.
 * Populated by {@link CustomerReportRowMapper} and written to CSV by the db-to-file-job.
 */
public class CustomerReportRecord {

    private String     customerId;
    private String     customerName;
    private String     region;
    private int        totalOrders;
    private BigDecimal totalAmount;
    private LocalDate  reportDate;

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public String     getCustomerId()                  { return customerId; }
    public void       setCustomerId(String v)          { this.customerId = v; }

    public String     getCustomerName()                { return customerName; }
    public void       setCustomerName(String v)        { this.customerName = v; }

    public String     getRegion()                      { return region; }
    public void       setRegion(String v)              { this.region = v; }

    public int        getTotalOrders()                 { return totalOrders; }
    public void       setTotalOrders(int v)            { this.totalOrders = v; }

    public BigDecimal getTotalAmount()                 { return totalAmount; }
    public void       setTotalAmount(BigDecimal v)     { this.totalAmount = v; }

    public LocalDate  getReportDate()                  { return reportDate; }
    public void       setReportDate(LocalDate v)       { this.reportDate = v; }
}
