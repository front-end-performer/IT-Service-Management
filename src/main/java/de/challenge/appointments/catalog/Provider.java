package de.challenge.appointments.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected Provider() {
    }

    public Provider(String id, String name, int capacity) {
        this.id = id;
        update(name, capacity);
    }

    void update(String name, int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.name = name;
        this.capacity = capacity;
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
}
