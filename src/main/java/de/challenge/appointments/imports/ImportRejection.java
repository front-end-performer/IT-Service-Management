package de.challenge.appointments.imports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_rejection")
public class ImportRejection {

    private static final int MAX_RAW_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "raw_line")
    private String rawLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RejectionReason reason;

    @Column(nullable = false)
    private String message;

    protected ImportRejection() {
    }

    ImportRejection(long lineNumber, String rawLine, RejectionReason reason, String message) {
        this.lineNumber = Math.toIntExact(lineNumber);
        this.rawLine = rawLine.length() > MAX_RAW_LENGTH ? rawLine.substring(0, MAX_RAW_LENGTH) : rawLine;
        this.reason = reason;
        this.message = message;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getRawLine() {
        return rawLine;
    }

    public RejectionReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }
}
