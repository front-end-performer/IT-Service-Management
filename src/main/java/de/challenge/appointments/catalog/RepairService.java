package de.challenge.appointments.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;

/**
 * A service offered by one provider. The same code (e.g. DIA) may exist at several
 * providers with different durations, so the code is only unique per provider.
 */
@Entity
@Table(name = "repair_service")
public class RepairService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    protected RepairService() {
    }

    public RepairService(Provider provider, String code, String name, int durationMinutes) {
        this.provider = provider;
        this.code = code;
        update(name, durationMinutes);
    }

    void update(String name, int durationMinutes) {
        if (durationMinutes < 1) {
            throw new IllegalArgumentException("durationMinutes must be at least 1");
        }
        this.name = name;
        this.durationMinutes = durationMinutes;
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Duration getDuration() {
        return Duration.ofMinutes(durationMinutes);
    }
}
