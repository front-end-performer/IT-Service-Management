package de.challenge.appointments;

import de.challenge.appointments.catalog.ProviderRepository;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.springframework.transaction.support.TransactionTemplate;

/** Holds a provider's row lock in a separate transaction for a while, to observe who waits for it. */
public final class LockProbe {

    private LockProbe() {
    }

    /** Returns once the lock is held; the future completes when the holding transaction has committed. */
    public static CompletableFuture<Void> holdProviderLock(TransactionTemplate transactions, ProviderRepository providers,
                                                           String providerId, Duration holdFor) throws InterruptedException {
        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> released = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(status -> {
            providers.findByIdForUpdate(providerId).orElseThrow();
            locked.countDown();
            try {
                Thread.sleep(holdFor.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        locked.await();
        return released;
    }
}
