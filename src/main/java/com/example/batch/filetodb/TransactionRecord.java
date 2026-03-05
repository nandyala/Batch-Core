package com.example.batch.filetodb;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents one line from the incoming CSV transaction file.
 * Populated by {@link TransactionFieldSetMapper} and written to the
 * TRANSACTION_STAGING table by the file-to-db-job.
 *
 * <p>Expected CSV columns (header row required):
 * <pre>
 *   transaction_id, account_number, amount, transaction_type, transaction_date, description
 * </pre>
 */
public class TransactionRecord {

    private String     transactionId;
    private String     accountNumber;
    private BigDecimal amount;
    /** CREDIT or DEBIT */
    private String     transactionType;
    private LocalDate  transactionDate;
    private String     description;

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public String     getTransactionId()               { return transactionId; }
    public void       setTransactionId(String v)       { this.transactionId = v; }

    public String     getAccountNumber()               { return accountNumber; }
    public void       setAccountNumber(String v)       { this.accountNumber = v; }

    public BigDecimal getAmount()                      { return amount; }
    public void       setAmount(BigDecimal v)          { this.amount = v; }

    public String     getTransactionType()             { return transactionType; }
    public void       setTransactionType(String v)     { this.transactionType = v; }

    public LocalDate  getTransactionDate()             { return transactionDate; }
    public void       setTransactionDate(LocalDate v)  { this.transactionDate = v; }

    public String     getDescription()                 { return description; }
    public void       setDescription(String v)         { this.description = v; }
}
