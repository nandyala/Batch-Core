package com.example.batch.sample;

/**
 * Simple domain object used by the sample job.
 * Replace with your own domain model.
 */
public class SampleRecord {

    private int    id;
    private String name;
    private String email;
    private String status;

    public SampleRecord() {}

    public SampleRecord(int id, String name, String email, String status) {
        this.id     = id;
        this.name   = name;
        this.email  = email;
        this.status = status;
    }

    public int    getId()              { return id; }
    public void   setId(int id)        { this.id = id; }

    public String getName()            { return name; }
    public void   setName(String name) { this.name = name; }

    public String getEmail()               { return email; }
    public void   setEmail(String email)   { this.email = email; }

    public String getStatus()              { return status; }
    public void   setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "SampleRecord{id=" + id + ", name='" + name + "', status='" + status + "'}";
    }
}
