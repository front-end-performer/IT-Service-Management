package de.challenge.appointments.catalog;

import de.challenge.appointments.scheduling.OpeningHours;
import de.challenge.appointments.scheduling.OpeningHours.Period;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** An IT service provider ("it-service" in the REST API; the mapping lives in the DTO layer only). */
@Entity
@Table(name = "provider")
public class Provider {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "time_zone", nullable = false)
    private String timeZone;

    @ElementCollection
    @CollectionTable(name = "provider_opening_hours", joinColumns = @JoinColumn(name = "provider_id"))
    private List<OpeningPeriod> openingPeriods = new ArrayList<>();

    /** One row of {@code provider_opening_hours}: local wall-clock times, deliberately not instants. */
    @Embeddable
    static class OpeningPeriod {

        @Enumerated(EnumType.STRING)
        @Column(name = "day_of_week", nullable = false)
        private DayOfWeek dayOfWeek;

        @Column(name = "opens_at", nullable = false)
        private LocalTime opensAt;

        @Column(name = "closes_at", nullable = false)
        private LocalTime closesAt;

        protected OpeningPeriod() {
        }

        OpeningPeriod(DayOfWeek dayOfWeek, Period period) {
            this.dayOfWeek = dayOfWeek;
            this.opensAt = period.opens();
            this.closesAt = period.closes();
        }
    }

    protected Provider() {
    }

    public Provider(String id, String name, int capacity, OpeningHours openingHours) {
        this.id = id;
        update(name, capacity, openingHours);
    }

    void update(String name, int capacity, OpeningHours openingHours) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.name = name;
        this.capacity = capacity;
        this.timeZone = openingHours.zone().getId();
        this.openingPeriods.clear();
        openingHours.periods().forEach((day, period) -> openingPeriods.add(new OpeningPeriod(day, period)));
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public OpeningHours getOpeningHours() {
        if (openingPeriods.isEmpty()) {
            return OpeningHours.ALWAYS;
        }
        Map<DayOfWeek, Period> periods = new EnumMap<>(DayOfWeek.class);
        openingPeriods.forEach(p -> periods.put(p.dayOfWeek, new Period(p.opensAt, p.closesAt)));
        return OpeningHours.of(ZoneId.of(timeZone), periods);
    }
}
