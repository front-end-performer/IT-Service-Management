package de.challenge.appointments.imports;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, String> {

    List<ImportBatch> findByProviderIdOrderByImportedAtDesc(String providerId);

    @EntityGraph(attributePaths = "rejections")
    Optional<ImportBatch> findByIdAndProviderId(String id, String providerId);
}
