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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit;
import es.deusto.prog3.githubanalyzer.gui.MainWindow.ContributionBadge;

class AuthorshipAppearanceTest {
    private final List<AuthorIdentity> identities = java.util.stream.IntStream.range(0, 6)
            .mapToObj(index -> new AuthorIdentity("Person " + index, "person" + index + "@example.org", "person" + index)).toList();

    private RepositorySnapshot source() {
        List<Commit> commits = new ArrayList<>();
        int[] churn = {10, 60, 100, 230, 500, 1000};
        for (int index = 0; index < churn.length; index++)
            commits.add(new Commit("commit" + index, identities.get(index), 1000 + index,
                    index == 0 ? List.of() : List.of("commit" + (index - 1)),
                    List.of(new ChangedFile("Example.java", "", churn[index], 0))));
        return new RepositorySnapshot(1, "https://github.com/course/appearance", "appearance", 3000, 1, 2000, true,
                "main", Map.of("main", "commit5"), commits, List.of());
    }

    @Test void badgesAndColorsMatchTheMainTableIncludingSelectionAndReorderedColumns() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<AuthorshipForm> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig config = new AuthorshipConfig();
            config.discover(identities.get(4), Role.TEACHER);
            config.discover(identities.get(5), Role.EXCLUDED);
            AuthorshipForm form = new AuthorshipForm(source(), config, 4);
            view.set(form);
            form.people.setRowSelectionInterval(0, 0);
            JLabel pending = (JLabel) form.people.prepareRenderer(form.people.getCellRenderer(1, 0), 1, 0);
            assertNull(pending.getIcon(), "Do not classify contribution before churn is available");
            assertTrue(form.contributionHint.getText().contains("Calculating"));
            form.previewStatus.addPropertyChangeListener("text", event -> {
                if (event.getNewValue().toString().startsWith("Local preview ·")) completed.countDown();
            });
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = view.get();
            ContributionBadge[] badges = {ContributionBadge.VERY_LOW, ContributionBadge.BELOW,
                    ContributionBadge.BALANCED, ContributionBadge.HIGH, ContributionBadge.TEACHER, ContributionBadge.EXCLUDED};
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.moveColumn(0, 4);
            for (int modelRow = 0; modelRow < badges.length; modelRow++) {
                int row = form.people.convertRowIndexToView(modelRow);
                int nameColumn = form.people.convertColumnIndexToView(0);
                JLabel label = (JLabel) form.people.prepareRenderer(form.people.getCellRenderer(row, nameColumn), row, nameColumn);
                ContributionBadge badge = badges[modelRow];
                assertNotNull(badge.icon(), "Missing contribution icon: " + badge.imageFile);
                assertEquals(28, badge.icon().getIconWidth());
                assertEquals(28, badge.icon().getIconHeight());
                assertSame(badge.icon(), label.getIcon());
                assertEquals(MainWindow.contributionBackground(form.people.getBackground(), badge.color,
                        form.people.isRowSelected(row)), label.getBackground());
                java.awt.Color rowBackground = label.getBackground();
                int commitsColumn = form.people.convertColumnIndexToView(3);
                JLabel number = (JLabel) form.people.prepareRenderer(form.people.getCellRenderer(row, commitsColumn), row, commitsColumn);
                assertEquals(rowBackground, number.getBackground());
                assertNull(number.getIcon());
                int shareColumn = form.people.convertColumnIndexToView(4);
                JLabel share = (JLabel) form.people.prepareRenderer(form.people.getCellRenderer(row, shareColumn), row, shareColumn);
                assertEquals(rowBackground, share.getBackground());
                if (modelRow >= 4) assertEquals("—", share.getText());
            }
            assertSame(ContributionBadge.VERY_LOW.icon(), form.contributionHint.getIcon());
            assertTrue(form.contributionHint.getText().contains("2,50 %"));
            int row = form.people.convertRowIndexToView(3);
            form.people.setRowSelectionInterval(row, row);
            assertSame(ContributionBadge.HIGH.icon(), form.contributionHint.getIcon());
            assertTrue(form.contributionHint.getText().contains("57,50 %"));
            form.participation.setSelectedItem(Role.TEACHER);
            assertSame(ContributionBadge.TEACHER.icon(), form.contributionHint.getIcon());
            assertFalse(form.contributionHint.getText().contains("%"));
        });
    }
    @Test void compactBoldHeadersFollowModelColumnsAndKeepSortIndicators() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(List.of(), new AuthorshipConfig(), 4);
            var header = form.people.getTableHeader();
            assertTrue(header.getFont().isBold());
            assertEquals(form.people.getFont().getSize(), header.getFont().getSize());
            assertTrue(header.getPreferredSize().height >= 36);
            form.people.getRowSorter().setSortKeys(List.of(new javax.swing.RowSorter.SortKey(3, javax.swing.SortOrder.ASCENDING)));
            form.people.moveColumn(3, 0);
            JLabel commits = (JLabel) header.getDefaultRenderer().getTableCellRendererComponent(form.people,
                    form.people.getColumnName(0), false, false, -1, 0);
            assertTrue(commits.getFont().isBold());
            assertEquals("<html>Java<br>commits</html>", commits.getText());
            assertEquals("Java commits", commits.getToolTipText());
            assertNotNull(commits.getIcon());
        });
    }

    @Test void contributionPaletteKeepsReadableContrastAcrossThemesSelectionAndShareBars() {
        for (var base : List.of(java.awt.Color.WHITE, new java.awt.Color(238, 238, 238),
                new java.awt.Color(32, 33, 36), new java.awt.Color(80, 80, 80))) {
            for (ContributionBadge badge : ContributionBadge.values()) {
                for (boolean selected : new boolean[] {false, true}) {
                    var background = MainWindow.contributionBackground(base, badge.color, selected);
                    var foreground = MainWindow.contributionForeground(badge.color, background, selected);
                    var fill = MainWindow.contributionBarColor(badge.color, background, foreground, selected);
                    for (int alpha : new int[] {0, fill.getAlpha() / 2, fill.getAlpha()}) {
                        double weight = alpha / 255.0;
                        var bar = new java.awt.Color(
                                (int) Math.round(background.getRed() * (1 - weight) + badge.color.getRed() * weight),
                                (int) Math.round(background.getGreen() * (1 - weight) + badge.color.getGreen() * weight),
                                (int) Math.round(background.getBlue() * (1 - weight) + badge.color.getBlue() * weight));
                        double a = luminance(foreground), b = luminance(bar);
                        double contrast = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
                        assertTrue(contrast >= 4.5, badge + " contrast=" + contrast + " selected=" + selected
                                + " base=" + base + " alpha=" + alpha);
                    }
                }
            }
        }
        for (ContributionBadge badge : List.of(ContributionBadge.BALANCED, ContributionBadge.HIGH)) {
            var background = MainWindow.contributionBackground(java.awt.Color.WHITE, badge.color, false);
            var foreground = MainWindow.contributionForeground(badge.color, background, false);
            assertTrue(background.getGreen() > background.getRed());
            assertTrue(background.getGreen() > background.getBlue());
            assertTrue(foreground.getGreen() > foreground.getRed());
            assertTrue(foreground.getGreen() > foreground.getBlue());
        }
        var teacher = MainWindow.contributionBackground(java.awt.Color.WHITE, ContributionBadge.TEACHER.color, false);
        assertTrue(teacher.getRed() >= 245 && teacher.getGreen() >= 245 && teacher.getBlue() >= 245);
    }

    private static double luminance(java.awt.Color color) {
        double[] values = { color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0 };
        for (int i = 0; i < values.length; i++)
            values[i] = values[i] <= 0.04045 ? values[i] / 12.92 : Math.pow((values[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * values[0] + 0.7152 * values[1] + 0.0722 * values[2];
    }

}
