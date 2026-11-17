package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class HeavyCallThrottleTest {
    @Test void concurrentCallsConsumeSeparateIntervals() throws Exception {
        AtomicLong time = new AtomicLong();
        List<Long> waits = new ArrayList<>();
        HeavyCallThrottle throttle = new HeavyCallThrottle(120, time::get, nanos -> {
            waits.add(nanos);
            time.addAndGet(nanos);
        });
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < 10; j++) throttle.await();
                return null;
            }));
            start.countDown();
            for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
            assertEquals(39, waits.size());
            assertTrue(waits.stream().allMatch(n -> n == TimeUnit.MILLISECONDS.toNanos(120)));
            assertEquals(TimeUnit.MILLISECONDS.toNanos(4680), time.get());
        } finally { pool.shutdownNow(); }
    }

    @Test void delayedWakeUpDoesNotLetTheNextCallThroughImmediately() {
        AtomicLong time = new AtomicLong();
        AtomicLong sleeps = new AtomicLong();
        HeavyCallThrottle throttle = new HeavyCallThrottle(120, time::get, nanos -> {
            sleeps.incrementAndGet();
            time.addAndGet(nanos + TimeUnit.SECONDS.toNanos(1));
        });
        throttle.await();
        throttle.await();
        throttle.await();
        assertEquals(2, sleeps.get());
    }

    @Test void interruptionAbortsWaitAndPreservesTheFlag() {
        HeavyCallThrottle throttle = new HeavyCallThrottle(120, () -> 0,
                nanos -> { throw new InterruptedException(); });
        throttle.await();
        try {
            assertThrows(CancellationException.class, throttle::await);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        HeavyCallThrottle fresh = new HeavyCallThrottle(120);
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, fresh::await);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
