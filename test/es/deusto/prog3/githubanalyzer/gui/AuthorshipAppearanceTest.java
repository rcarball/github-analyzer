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
}
