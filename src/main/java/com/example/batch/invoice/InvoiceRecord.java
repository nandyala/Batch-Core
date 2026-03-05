package com.example.batch.invoice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Domain object representing a single invoice to be processed.
 * Replace / extend fields to match your actual INVOICE table schema.
 */
public class InvoiceRecord {

    private long        invoiceId;
    private String      customerId;
    private BigDecimal  amount;
    private LocalDate   invoiceDate;
    private String      status;        // e.g. PENDING, PROCESSED, FAILED

    public InvoiceRecord() {}

    public InvoiceRecord(long invoiceId, String customerId,
                         BigDecimal amount, LocalDate invoiceDate, String status) {
        this.invoiceId   = invoiceId;
        this.customerId  = customerId;
        this.amount      = amount;
        this.invoiceDate = invoiceDate;
        this.status      = status;
    }

    public long       getInvoiceId()               { return invoiceId; }
    public void       setInvoiceId(long v)          { this.invoiceId = v; }

    public String     getCustomerId()               { return customerId; }
    public void       setCustomerId(String v)       { this.customerId = v; }

    public BigDecimal getAmount()                   { return amount; }
    public void       setAmount(BigDecimal v)       { this.amount = v; }

    public LocalDate  getInvoiceDate()              { return invoiceDate; }
    public void       setInvoiceDate(LocalDate v)   { this.invoiceDate = v; }

    public String     getStatus()                   { return status; }
    public void       setStatus(String v)           { this.status = v; }

    @Override
    public String toString() {
        return "InvoiceRecord{id=" + invoiceId
               + ", customer=" + customerId
               + ", amount=" + amount
               + ", status=" + status + "}";
    }
}
