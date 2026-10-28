package de.challenge.appointments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/** Test helper for races: runs tasks on separate threads, all released at the same instant. */
public final class Simultaneously {

    private Simultaneously() {
    }

    /**
     * Creates {@code count} tasks via {@code task(index)}, starts them together and returns their
     * results in index order. An unexpected exception in any task fails the caller.
     */
    public static <T> List<T> run(int count, IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> callable = task.apply(i);
                futures.add(pool.submit(() -> {
                    start.await();
                    return callable.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
