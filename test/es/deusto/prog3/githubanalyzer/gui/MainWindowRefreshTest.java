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
        checkRefreshControls(0);
    }

    @Test
    public void failedRefreshAlsoUnlocksConfiguration() throws Exception {
        checkRefreshControls(1);
    }

    @Test public void csvFailureShowsCalculatedDataAndAnExportErrorBeforeUnlockingControls() throws Exception {
        checkRefreshControls(2);
    }

    private static void checkRefreshControls(int failureMode) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<JButton> refresh = new AtomicReference<>();
        AtomicReference<JButton> config = new AtomicReference<>();
        AtomicReference<JButton> authorship = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<List<RepoStats>> result = new AtomicReference<>();
        AtomicBoolean lockedDuringCompletion = new AtomicBoolean();
        AtomicReference<String> exportMessage = new AtomicReference<>(), exportTitle = new AtomicReference<>();
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
                    if (failureMode == 1) throw new IOException("Simulated refresh failure");
                    if (failureMode == 2) throw new es.deusto.prog3.githubanalyzer.persistence.CsvExportException(
                            "reports/stats.csv", expected, new IOException("Simulated permission failure"));
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
                        CsvExportFeedback.handle(ex, result::set, (title, message) -> {
                            if (!SwingUtilities.isEventDispatchThread() || refresh.get().isEnabled())
                                throw new IllegalStateException("Export feedback must run on the locked event thread");
                            exportTitle.set(title); exportMessage.set(message);
                        });
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
        if (failureMode == 2) {
            assertInstanceOf(es.deusto.prog3.githubanalyzer.persistence.CsvExportException.class, failure.get());
            assertEquals(expected, result.get());
            assertEquals("CSV export failed", exportTitle.get());
            assertTrue(exportMessage.get().contains("Calculated statistics remain available"));
            assertTrue(exportMessage.get().contains("reports/stats.csv"));
            assertTrue(exportMessage.get().contains("Simulated permission failure"));
            assertTrue(exportMessage.get().contains("previous CSV was not replaced"));
            assertFalse(exportMessage.get().contains("Repositories remain pending"));
        } else if (failureMode == 1) {
            assertInstanceOf(IOException.class, failure.get());
            assertNull(result.get());
            assertNull(exportTitle.get(), "Network failures must keep their normal error path");
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
        RepoStats updated = MainWindow.withConfiguredRepositories(List.of(stale), List.of(changedSize)).get(0);
        assertTrue(updated.isAnalysisComplete());
        assertEquals(99, updated.getCodeLines());
        assertEquals(5, updated.getTeamSize());
        assertEquals(4, stale.getTeamSize(), "Updating metadata must not mutate a prior view");
    }
    @Test public void treeSuffixDisplaysPendingCommitCountAndUnknownChecks() {
        assertEquals(" (12)", RepositoryTreeRenderer.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(12, true, null)));
        assertEquals("", RepositoryTreeRenderer.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, false, null)));
        assertEquals(" (changes pending)", RepositoryTreeRenderer.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, true, null)));
        assertEquals(" (?)", RepositoryTreeRenderer.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(-1, false, "Offline")));
        assertEquals("", RepositoryTreeRenderer.pendingSuffix(null));
    }

    @Test public void externalGroupAndSizeUpdatesKeepMetricsAndRemoveUnconfiguredRepositories() {
        String url = "https://github.com/course/project";
        RepoStats previous = new RepoStats(); previous.setUrl(url); previous.setGroup("old"); previous.setTeamSize(2);
        previous.setAnalysisComplete(true); previous.setCommits(12); previous.setLinesChanged(150); previous.setDataCapturedAt(1234);
        previous.addUserStats(new es.deusto.prog3.githubanalyzer.domain.UserStats("member", "m@example.org", 2, 1, 100, 0, 100, 100, 200));
        RepoStats removed = new RepoStats(); removed.setUrl(url + "-removed"); removed.setAnalysisComplete(true);
        var settings = new es.deusto.prog3.githubanalyzer.domain.RepositoryConfig(url, "new-group", 4);
        List<RepoStats> displayed = MainWindow.withConfiguredRepositories(List.of(previous, removed), List.of(settings));
        assertEquals(1, displayed.size()); RepoStats current = displayed.get(0);
        assertTrue(current.isAnalysisComplete()); assertEquals("new-group", current.getGroup()); assertEquals(4, current.getTeamSize());
        assertEquals(12, current.getCommits()); assertEquals(150, current.getLinesChanged()); assertEquals(1234, current.getDataCapturedAt());
        assertEquals(.25f, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.expectedShare(current));
        assertEquals(1f, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.teamShare(current, current.getUserStats().get(0)));
        assertEquals("old", previous.getGroup()); assertEquals(2, previous.getTeamSize());
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

            suffix.set(RepositoryTreeRenderer.pendingSuffix(new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, true, null)));
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
    @Test public void pendingRefreshIncludesMissingFailedAndChangedButSkipsCurrentAndConfirmedEmptyRepositories() {
        var missing = repository("missing", false, 0);
        var current = repository("current", true, 2);
        var commits = repository("commits", true, 2);
        var metadata = repository("metadata", true, 2);
        var failed = repository("failed", true, 2);
        var empty = repository("empty", true, 0);
        var unknown = repository("unknown", true, 2);
        var updates = java.util.Map.of(
                "current", new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, false, null),
                "commits", new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(4, true, null),
                "metadata", new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, true, null),
                "empty", new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(0, false, null),
                "unknown", new es.deusto.prog3.githubanalyzer.GitHubDataLoader.UpdateStatus(-1, false, "Offline"));
        assertEquals(List.of("missing", "commits", "metadata", "failed", "unknown"), MainWindow.pendingRefreshTargets(
                List.of(missing, current, commits, metadata, failed, empty, unknown), updates, java.util.Set.of("failed")));
    }

    @Test public void pendingProjectionKeepsKnownVisibilityWithoutReusingStaleMetrics() {
        RepoStats pending = repository("https://github.com/course/repo", false, 20);
        pending.setPublic(true);
        var config = new es.deusto.prog3.githubanalyzer.domain.RepositoryConfig(pending.getUrl(), "G01", 4);
        RepoStats displayed = MainWindow.withConfiguredRepositories(List.of(pending), List.of(config)).get(0);
        assertTrue(displayed.isPublic());
        assertFalse(displayed.isAnalysisComplete());
        assertEquals(0, displayed.getCommits());
        RepoStats rebuilt = MainWindow.withConfiguredRepositories(List.of(), List.of(config),
                java.util.Map.of(pending.getUrl(), true)).get(0);
        assertTrue(rebuilt.isPublic(), "A failed first download must not replace known public visibility");
    }

    private static RepoStats repository(String url, boolean complete, int commits) {
        RepoStats result = new RepoStats();
        result.setUrl(url); result.setName(url); result.setAnalysisComplete(complete); result.setCommits(commits);
        return result;
    }

}
