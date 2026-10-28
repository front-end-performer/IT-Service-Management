package de.challenge.appointments.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findByProviderIdOrderByCode(String providerId);

    Optional<ServiceOffering> findByProviderIdAndCode(String providerId, String code);
}
