package de.challenge.appointments.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepairServiceRepository extends JpaRepository<RepairService, Long> {

    List<RepairService> findByProviderIdOrderByCode(String providerId);

    Optional<RepairService> findByProviderIdAndCode(String providerId, String code);
}
