package de.challenge.appointments.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProviderRepository extends JpaRepository<Provider, String> {

    /**
     * Loads the provider with {@code SELECT … FOR UPDATE}. Every write to a provider's
     * schedule takes this lock first, so capacity checks and inserts for the same provider
     * run one after another until the transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Provider p where p.id = :id")
    Optional<Provider> findByIdForUpdate(String id);
}
