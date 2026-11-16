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
        AtomicReference<JButton> authorship = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<RepoStats>> result = new AtomicReference<>();
        AtomicBoolean lockedDuringCompletion = new AtomicBoolean();
        List<RepoStats> expected = List.of(new RepoStats());

        SwingUtilities.invokeAndWait(() -> {
            refresh.set(new JButton("Refresh"));
            config.set(new JButton("Config"));
            authorship.set(new JButton("Authorship"));
            MainWindow.RefreshWorker worker = new MainWindow.RefreshWorker(refresh.get(), config.get(), authorship.get()) {
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
                assertFalse(authorship.get().isEnabled());
            });
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS), "The completion callback must finish");
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(refresh.get().isEnabled());
            assertTrue(config.get().isEnabled());
            assertTrue(authorship.get().isEnabled());
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
    @Test
    public void configuredRepositoriesWithoutFreshResultsAreSelectableButPending() {
        var config = new es.deusto.prog3.githubanalyzer.domain.RepositoryConfig("https://github.com/course/repo", "G01", 4);
        RepoStats stale = new RepoStats(); stale.setUrl(config.url()); stale.setCodeLines(99);
        List<RepoStats> displayed = MainWindow.withConfiguredRepositories(List.of(stale), List.of(config));
        assertEquals(1, displayed.size());
        assertFalse(displayed.get(0).isAnalysisComplete());
        assertEquals(0, displayed.get(0).getCodeLines());
        assertEquals(4, displayed.get(0).getTeamSize());
        stale.setAnalysisComplete(true); stale.setGroup("G01"); stale.setTeamSize(4);
        assertEquals(stale, MainWindow.withConfiguredRepositories(List.of(stale), List.of(config)).get(0));
        var changedSize = new es.deusto.prog3.githubanalyzer.domain.RepositoryConfig(config.url(), "G01", 5);
        assertFalse(MainWindow.withConfiguredRepositories(List.of(stale), List.of(changedSize)).get(0).isAnalysisComplete());
    }
    @Test public void treeSuffixDisplaysPendingCommitCountAndUnknownChecks() {
        assertEquals(" (12)", MainWindow.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(12, true, null)));
        assertEquals(" (0)", MainWindow.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, false, null)));
        assertEquals(" (0; changes pending)", MainWindow.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, true, null)));
        assertEquals(" (?)", MainWindow.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(-1, false, "Offline")));
        assertEquals("", MainWindow.pendingSuffix(null));
    }

    @Test public void updatedTreeLabelsGrowAndScrollWithoutLosingSelectionOrExpansion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var root = new javax.swing.tree.DefaultMutableTreeNode("Repositories");
            var node = new javax.swing.tree.DefaultMutableTreeNode("course-project-with-a-long-name");
            root.add(node);
            var tree = new javax.swing.JTree(root);
            AtomicReference<String> suffix = new AtomicReference<>("");
            tree.setCellRenderer(new javax.swing.tree.DefaultTreeCellRenderer() {
                private static final long serialVersionUID = 1L;
                @Override public java.awt.Component getTreeCellRendererComponent(javax.swing.JTree view, Object value,
                        boolean selected, boolean expanded, boolean leaf, int row, boolean focused) {
                    super.getTreeCellRendererComponent(view, value, selected, expanded, leaf, row, focused);
                    if (value == node) setText(node.getUserObject() + suffix.get());
                    return this;
                }
            });
            var scroll = MainWindow.repositoryTreeScrollPane(tree);
            // Start with room for the plain label, independently of the sidebar's configured width.
            scroll.setSize(tree.getPreferredSize().width + 30, 180);
            layoutTreeScrollPane(scroll);
            var path = new javax.swing.tree.TreePath(node.getPath());
            tree.setSelectionPath(path);
            int initialWidth = tree.getPathBounds(path).width;
            assertFalse(scroll.getHorizontalScrollBar().isVisible());

            suffix.set(MainWindow.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, true, null)));
            MainWindow.refreshRepositoryTreeLabels(tree);
            layoutTreeScrollPane(scroll);
            assertTrue(tree.getPathBounds(path).width > initialWidth, "Recompute node bounds when the update suffix appears");
            assertTrue(scroll.getHorizontalScrollBar().isVisible(), "Long update labels must be horizontally scrollable");
            scroll.getHorizontalScrollBar().setValue(scroll.getHorizontalScrollBar().getMaximum());
            assertTrue(scroll.getViewport().getViewPosition().x > 0);
            assertEquals(path, tree.getSelectionPath());
            assertTrue(tree.isExpanded(0));

            suffix.set("");
            MainWindow.refreshRepositoryTreeLabels(tree);
            layoutTreeScrollPane(scroll);
            assertEquals(initialWidth, tree.getPathBounds(path).width);
            assertFalse(scroll.getHorizontalScrollBar().isVisible());
            assertEquals(path, tree.getSelectionPath());
        });
    }

    private static void layoutTreeScrollPane(javax.swing.JScrollPane scroll) {
        scroll.doLayout();
        scroll.getViewport().doLayout();
        scroll.doLayout();
        scroll.getViewport().doLayout();
    }
}
