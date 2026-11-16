package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/** Exercises the real SwingWorker lifecycle without creating windows or using the network. */
public class MainWindowRefreshTest {
    @Test
    public void configurationStaysLockedUntilSuccessfulRefreshCompletes() throws Exception {
        checkRefreshControls(false);
    }

    @Test
    public void failedRefreshAlsoUnlocksConfiguration() throws Exception {
        checkRefreshControls(true);
    }

    private static void checkRefreshControls(boolean fail) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<JButton> refresh = new AtomicReference<>();
        AtomicReference<JButton> config = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<RepoStats>> result = new AtomicReference<>();
        AtomicBoolean lockedDuringCompletion = new AtomicBoolean();
        List<RepoStats> expected = List.of(new RepoStats());

        SwingUtilities.invokeAndWait(() -> {
            refresh.set(new JButton("Refresh"));
            config.set(new JButton("Config"));
            MainWindow.RefreshWorker worker = new MainWindow.RefreshWorker(refresh.get(), config.get()) {
                @Override
                protected List<RepoStats> doInBackground() throws Exception {
                    started.countDown();
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test refresh timed out");
                    if (fail) throw new IOException("Simulated refresh failure");
                    return expected;
                }

                @Override
                protected void refreshFinished() {
                    lockedDuringCompletion.set(SwingUtilities.isEventDispatchThread()
                            && !refresh.get().isEnabled() && !config.get().isEnabled());
                    try {
                        result.set(get());
                    } catch (ExecutionException ex) {
                        failure.set(ex.getCause());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        failure.set(ex);
                    }
                }
            };
            worker.addPropertyChangeListener(event -> {
                if ("state".equals(event.getPropertyName()) && event.getNewValue() == SwingWorker.StateValue.DONE) {
                    finished.countDown();
                }
            });
            worker.execute();
        });

        try {
            assertTrue(started.await(5, TimeUnit.SECONDS), "The background refresh must start");
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(refresh.get().isEnabled());
                assertFalse(config.get().isEnabled(), "Configuration must be unavailable during refresh");
            });
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS), "The completion callback must finish");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(refresh.get().isEnabled());
            assertTrue(config.get().isEnabled());
        });
        assertTrue(lockedDuringCompletion.get(), "Controls must stay locked throughout completion handling on the EDT");
        if (fail) {
            assertInstanceOf(IOException.class, failure.get());
            assertNull(result.get());
        } else {
            assertNull(failure.get());
            assertEquals(expected, result.get());
        }
    }
}
