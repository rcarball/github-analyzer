// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI ASSISTANCE AND REVIEW DISCLOSURE
 *
 * The initial version of this codebase was developed in 2024 with partial
 * assistance from ChatGPT (OpenAI).
 *
 * From July to September 2026, the codebase was reviewed and audited using
 * Claude Code (Anthropic) and Codex (OpenAI).
 *
 * The resulting version was reviewed, tested, and refined to identify and
 * correct issues within the scope of the performed verification activities.
 */

package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.persistence.AuthorshipStore;

/** Exercises the actual loading component without windows, credentials or HTTP requests. */
class AuthorshipDiscoveryPanelTest {
    private static AuthorshipDiscoveryPanel.Result result(Path directory) {
        return new AuthorshipDiscoveryPanel.Result(List.of(), new AuthorshipConfig(), new AuthorshipStore(directory), 4);
    }

    @Test void loadingIsVisibleAndEdtResponsiveBeforeDiscoveryCompletes(@TempDir Path directory) throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), loaded = new CountDownLatch(1), progress = new CountDownLatch(1);
        AtomicReference<AuthorshipDiscoveryPanel> panel = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipDiscoveryPanel view = new AuthorshipDiscoveryPanel(report -> {
                assertFalse(SwingUtilities.isEventDispatchThread());
                calls.incrementAndGet();
                started.countDown();
                report.accept("Reading commits…");
                if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test release timed out");
                return result(directory);
            }, value -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                assertEquals(4, value.teamSize());
                loaded.countDown();
            }, failure::set, () -> { });
            panel.set(view);
            view.status.addPropertyChangeListener("text", event -> {
                if ("Reading commits…".equals(event.getNewValue())) progress.countDown();
            });
            assertFalse(view.status.getText().isBlank());
            assertTrue(view.cancelButton.isEnabled());
            assertEquals(0, calls.get(), "Constructing the visible loading panel must not start any I/O");
            view.start();
            view.start();
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTrue(progress.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("Reading commits…", panel.get().status.getText());
                assertTrue(panel.get().cancelButton.isEnabled());
                assertEquals(1, loaded.getCount(), "The event thread must remain usable while discovery waits");
            });
        } finally { release.countDown(); }
        assertTrue(loaded.await(5, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertEquals(1, calls.get());
    }

    @Test void cancellationClosesImmediatelyAndIgnoresALateUninterruptibleResponse(@TempDir Path directory) throws Exception {
        CountDownLatch started = new CountDownLatch(1), finished = new CountDownLatch(1);
        Semaphore release = new Semaphore(0);
        AtomicReference<AuthorshipDiscoveryPanel> panel = new AtomicReference<>();
        AtomicInteger cancelled = new AtomicInteger(), loaded = new AtomicInteger(), failed = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new AuthorshipDiscoveryPanel(report -> {
                started.countDown();
                try {
                    release.acquireUninterruptibly();
                    return result(directory);
                } finally { finished.countDown(); }
            }, value -> loaded.incrementAndGet(), error -> failed.incrementAndGet(), cancelled::incrementAndGet));
            panel.get().start();
        });
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                panel.get().cancelButton.doClick();
                panel.get().cancel();
                assertEquals(1, cancelled.get(), "Cancellation must close the loading view without waiting for HTTP");
                assertEquals(1, finished.getCount(), "The simulated blocked request has not finished yet");
            });
        } finally { release.release(); }
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(0, loaded.get(), "A late result must not reopen the editor or save any rules");
            assertEquals(0, failed.get());
        });
    }

    @Test void discoveryFailureIsDeliveredOnEdtWithoutOpeningTheEditor() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        IOException expected = new IOException("Simulated GitHub error");
        AtomicReference<Throwable> actual = new AtomicReference<>();
        AtomicInteger loaded = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipDiscoveryPanel panel = new AuthorshipDiscoveryPanel(report -> { throw expected; },
                    result -> loaded.incrementAndGet(), error -> {
                        assertTrue(SwingUtilities.isEventDispatchThread());
                        actual.set(error);
                        failed.countDown();
                    }, () -> { });
            panel.start();
        });
        assertTrue(failed.await(5, TimeUnit.SECONDS));
        assertSame(expected, actual.get());
        assertEquals(0, loaded.get());
    }

    @Test void closingBeforeStartNeverStartsTheSource() throws Exception {
        AtomicInteger calls = new AtomicInteger(), cancelled = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipDiscoveryPanel panel = new AuthorshipDiscoveryPanel(report -> {
                calls.incrementAndGet(); return null;
            }, result -> fail("Cancelled discovery must not complete"), error -> fail(error), cancelled::incrementAndGet);
            panel.cancel();
            panel.start();
        });
        assertEquals(0, calls.get());
        assertEquals(1, cancelled.get());
    }
}
