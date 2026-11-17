package es.deusto.prog3.githubanalyzer;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/** Spaces expensive request admissions using a monotonic clock. */
final class HeavyCallThrottle {
    @FunctionalInterface
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }

    private final ReentrantLock lock = new ReentrantLock(true);
    private final long interval;
    private final LongSupplier clock;
    private final Sleeper sleeper;
    private boolean started;
    private long lastAdmission;

    HeavyCallThrottle(long intervalMillis) {
        this(intervalMillis, System::nanoTime, TimeUnit.NANOSECONDS::sleep);
    }

    HeavyCallThrottle(long intervalMillis, LongSupplier clock, Sleeper sleeper) {
        this.interval = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
        this.clock = clock;
        this.sleeper = sleeper;
    }

    void await() {
        try {
            lock.lockInterruptibly();
            try {
                // Waiters cannot wake together, even after a delayed wake-up.
                // Only the admission is serialized; requests run outside the lock.
                long now = clock.getAsLong();
                while (started && now - lastAdmission < interval) {
                    sleeper.sleep(interval - (now - lastAdmission));
                    now = clock.getAsLong();
                }
                lastAdmission = now;
                started = true;
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Analysis cancelled.");
        }
    }
}
