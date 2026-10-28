package de.challenge.appointments.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, String>, JpaSpecificationExecutor<Appointment> {

    /** Appointments of a provider that overlap {@code [from, to)}. */
    @Query("""
            select a from Appointment a
            where a.provider.id = :providerId and a.start < :to and a.end > :from
            """)
    List<Appointment> findOverlapping(String providerId, Instant from, Instant to);

    boolean existsByProviderIdAndServiceOfferingIdAndCustomerNumberAndStart(
            String providerId, Long repairServiceId, String customerNumber, Instant start);

    @EntityGraph(attributePaths = {"provider", "serviceOffering"})
    Optional<Appointment> findByIdAndProviderId(String id, String providerId);

    @Override
    @EntityGraph(attributePaths = {"provider", "serviceOffering"})
    List<Appointment> findAll(Specification<Appointment> spec, Sort sort);
}
