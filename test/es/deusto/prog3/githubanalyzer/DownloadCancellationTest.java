// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */

package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;

class DownloadCancellationTest {
    @Test void cancellationInterruptsDownloadsAndSkipsFinalPersistence() throws Exception {
        List<String> urls = IntStream.range(0, 12)
                .mapToObj(i -> "https://github.com/course/repo" + i).toList();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger interrupted = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        AtomicInteger csvWrites = new AtomicInteger();
        AtomicInteger snapshotWrites = new AtomicInteger();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean flagPreserved = new AtomicBoolean();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        GitHubDataLoader loader = new GitHubDataLoader(() -> urls, url -> new AuthorshipConfig(),
                () -> url -> {
                    active.incrementAndGet();
                    entered.countDown();
                    try {
                        release.await();
                        throw new IOException("Test released");
                    } catch (InterruptedException ex) {
                        interrupted.incrementAndGet();
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted request", ex);
                    } finally { active.decrementAndGet(); }
                }, stats -> writes.incrementAndGet(), stats -> csvWrites.incrementAndGet(),
                url -> null, raw -> snapshotWrites.incrementAndGet());
        Thread caller = new Thread(() -> {
            try { loader.loadData(); }
            catch (CancellationException ex) {
                cancelled.set(true);
                flagPreserved.set(Thread.currentThread().isInterrupted());
            } catch (Throwable ex) { unexpected.set(ex); }
        });
        try {
            caller.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            caller.interrupt();
            caller.join(5000);
            assertFalse(caller.isAlive(), "Cancellation must return without waiting for all repositories");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (active.get() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertEquals(0, active.get(), "In-flight requests must receive interruption");
            assertTrue(interrupted.get() > 0);
            assertTrue(cancelled.get());
            assertTrue(flagPreserved.get());
            assertNull(unexpected.get());
            assertEquals(0, writes.get());
            assertEquals(0, csvWrites.get());
            assertEquals(0, snapshotWrites.get());
        } finally {
            release.countDown();
            caller.interrupt();
            caller.join(5000);
        }
    }

    @Test void alreadyInterruptedLoadDoesNotInitializeApiAccess() {
        AtomicInteger reads = new AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of("https://github.com/course/repo"),
                url -> new AuthorshipConfig(), () -> {
                    reads.incrementAndGet();
                    throw new AssertionError("Unexpected API access");
                }, stats -> fail("Unexpected write"), stats -> fail("Unexpected CSV"));
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, loader::loadData);
            assertEquals(0, reads.get());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }
}
