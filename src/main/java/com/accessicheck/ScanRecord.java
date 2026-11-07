package com.accessicheck;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ScanRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String scanDate;
    private int score;
    private int issueCount;
    private String scanSource;

    public ScanRecord() {}

    public ScanRecord(String scanDate, int score, int issueCount) {
        this(scanDate, score, issueCount, "Sample data");
    }

    public ScanRecord(String scanDate, int score, int issueCount, String scanSource) {
        this.scanDate = scanDate;
        this.score = score;
        this.issueCount = issueCount;
        this.scanSource = scanSource;
    }

    public Long getId() { return id; }
    public String getScanDate() { return scanDate; }
    public int getScore() { return score; }
    public int getIssueCount() { return issueCount; }
    public String getScanSource() { return scanSource == null ? "Pasted HTML" : scanSource; }
}
