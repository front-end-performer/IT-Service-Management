package de.challenge.appointments.appointment;

import de.challenge.appointments.catalog.Provider;
import de.challenge.appointments.catalog.RepairService;
import de.challenge.appointments.scheduling.TimeSlot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repair_service_id")
    private RepairService repairService;

    @Column(name = "customer_number", nullable = false)
    private String customerNumber;

    @Column(name = "start_at", nullable = false)
    private Instant start;

    /** Derived from the service duration at scheduling time; stored so overlap queries stay index-friendly. */
    @Column(name = "end_at", nullable = false)
    private Instant end;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentSource source;

    @Column(name = "import_batch_id")
    private String importBatchId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Appointment() {
    }

    Appointment(RepairService repairService, String customerNumber, Instant start,
                AppointmentSource source, String importBatchId, Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.provider = repairService.getProvider();
        this.repairService = repairService;
        this.customerNumber = customerNumber;
        this.start = start;
        this.end = start.plus(repairService.getDuration());
        this.source = source;
        this.importBatchId = importBatchId;
        this.createdAt = createdAt;
    }

    public TimeSlot slot() {
        return new TimeSlot(start, end);
    }

    public String getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public RepairService getRepairService() {
        return repairService;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public Instant getStart() {
        return start;
    }

    public Instant getEnd() {
        return end;
    }

    public AppointmentSource getSource() {
        return source;
    }

    public String getImportBatchId() {
        return importBatchId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
