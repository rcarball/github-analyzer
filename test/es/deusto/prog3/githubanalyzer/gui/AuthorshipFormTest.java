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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit;

class AuthorshipFormTest {
    private final AuthorIdentity a = new AuthorIdentity("Name", "school@example.org", "login");
    private final AuthorIdentity b = new AuthorIdentity("Alias", "home@example.org", "personal");
    private final AuthorIdentity c = new AuthorIdentity("Third", "third@example.org", "third");
    private final List<AuthorSummary> authors = List.of(new AuthorSummary(a, 3), new AuthorSummary(b, 2), new AuthorSummary(c, 1));

    private static List<String> options(JComboBox<?> combo) {
        return java.util.stream.IntStream.range(0, combo.getItemCount()).mapToObj(index -> combo.getItemAt(index).toString()).toList();
    }

    private static void editName(AuthorshipForm form, String name) {
        if (!form.nameField.isEditable()) form.editNameButton.doClick();
        form.nameField.getEditor().setItem(name);
    }

    private static void assertRules(AuthorshipConfig expected, AuthorshipConfig actual) {
        assertEquals(expected.assignments(), actual.assignments());
        assertEquals(expected.people(), actual.people());
    }

    private static void select(AuthorshipForm form, String... ids) {
        form.people.getSelectionModel().setValueIsAdjusting(true);
        form.people.clearSelection();
        AuthorshipConfig config = form.snapshot();
        for (int row = 0; row < form.people.getRowCount(); row++) {
            // Names are only used to find the model's stable person ID in this test fixture.
            String name = form.people.getValueAt(row, form.people.convertColumnIndexToView(0)).toString();
            boolean selected = java.util.Arrays.stream(ids).anyMatch(id -> config.people().get(id).name().equals(name));
            if (selected) form.people.addRowSelectionInterval(row, row);
        }
        form.people.getSelectionModel().setValueIsAdjusting(false);
    }

    private static void destination(AuthorshipForm form, String id) {
        for (int index = 0; index < form.mergeTarget.getItemCount(); index++) {
            if (form.mergeTarget.getItemAt(index).id().equals(id)) {
                form.mergeTarget.setSelectedIndex(index);
                return;
            }
        }
        fail("Destination missing: " + id);
    }

    @Test void tableAndDetailPanelOfferGroupedMetadataAndPersistItAcrossReopening() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig input = new AuthorshipConfig();
            AuthorshipForm form = new AuthorshipForm(authors, input, 4);
            String first = form.snapshot().personFor(a).id();
            String second = form.snapshot().personFor(b).id();
            assertEquals(List.of("Person", "Role", "Identities", "Java commits", "Observed share"),
                    java.util.stream.IntStream.range(0, 5).mapToObj(form.people::getColumnName).toList());
            select(form, first, second);
            assertNull(form.mergeTarget.getSelectedItem());
            assertTrue(form.unifyButton.isEnabled());
            assertEquals("Combine selected…", form.unifyButton.getText());
            destination(form, first); assertTrue(form.unifyButton.isEnabled());
            form.unifyButton.doClick();
            assertEquals(2, form.identities.getModel().getSize());
            assertTrue(options(form.nameField).containsAll(List.of("Name", "Alias", "login", "personal")));
            assertEquals(List.of(a.email(), b.email()), options(form.emailField));
            assertEquals(List.of(a.login(), b.login()), options(form.accountField));
            editName(form, "Custom name");
            form.emailField.setSelectedItem(b.email());
            form.accountField.setSelectedItem(b.login());
            form.participation.setSelectedItem(Role.TEACHER);
            AuthorshipConfig saved = form.snapshot();
            assertEquals("Custom name", saved.personFor(a).name());
            assertEquals(b.email(), saved.personFor(a).email());
            assertEquals(b.login(), saved.personFor(a).login());
            assertEquals(Role.TEACHER, saved.personFor(a).role());
            assertTrue(form.changeSummary().contains("Email:"));
            AuthorshipForm reopened = new AuthorshipForm(authors, saved, 4);
            select(reopened, first);
            assertEquals("Custom name", reopened.nameField.getSelectedItem());
            assertEquals(b.email(), reopened.emailField.getSelectedItem());
            assertEquals(b.login(), reopened.accountField.getSelectedItem());
            assertEquals(Role.TEACHER, reopened.participation.getSelectedItem());
            assertTrue(input.assignments().isEmpty());
        });
    }

    @Test void incrementalCombinationSupportsExactUndoRedoAndUndoableSessionReset() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            String first = initial.personFor(a).id(), second = initial.personFor(b).id(), third = initial.personFor(c).id();
            select(form, first, second); destination(form, second); form.unifyButton.doClick();
            AuthorshipConfig pair = form.snapshot();
            assertEquals(2, form.contributorCount());
            select(form, second, third); destination(form, second); form.unifyButton.doClick();
            AuthorshipConfig all = form.snapshot();
            assertEquals(1, form.contributorCount()); assertEquals(3, form.identities.getModel().getSize());
            form.undoButton.doClick(); assertRules(pair, form.snapshot());
            form.undoButton.doClick(); assertRules(initial, form.snapshot());
            form.redoButton.doClick(); assertRules(pair, form.snapshot());
            form.redoButton.doClick(); assertRules(all, form.snapshot());
            form.discardButton.doClick(); assertRules(initial, form.snapshot());
            assertFalse(form.discardButton.isEnabled());
            form.undoButton.doClick(); assertRules(all, form.snapshot());
        });
    }

    @Test void sortingFilteringAndReorderedColumnsDoNotChangeTheChosenDestination() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig original = form.snapshot();
            String first = original.personFor(a).id(), second = original.personFor(b).id();
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.moveColumn(0, 4);
            select(form, first, second); destination(form, second); form.unifyButton.doClick();
            assertEquals(second, form.snapshot().personFor(a).id());
            form.searchField.setText(a.email());
            assertEquals(1, form.people.getRowCount());
            assertEquals(second, form.snapshot().personFor(a).id());
            form.searchField.setText("no match"); assertEquals(0, form.people.getRowCount());
            form.undoButton.doClick();
            form.searchField.setText(""); assertEquals(3, form.people.getRowCount());
            assertRules(original, form.snapshot());
        });
    }

    @Test void mixingRolesRequiresExplicitParticipationAndDoesNotAdoptTheTargetSilently() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig saved = new AuthorshipConfig();
            saved.discover(a, Role.MEMBER); saved.discover(b, Role.TEACHER);
            AuthorshipForm form = new AuthorshipForm(authors, saved, null);
            AuthorshipConfig initial = form.snapshot();
            select(form, initial.personFor(a).id(), initial.personFor(b).id());
            destination(form, initial.personFor(a).id());
            assertNull(form.mergeRole.getSelectedItem()); assertTrue(form.unifyButton.isEnabled());
            assertEquals("Combine selected…", form.unifyButton.getText());
            form.unifyButton.doClick(); assertRules(initial, form.snapshot());
            form.mergeRole.setSelectedItem(Role.EXCLUDED); assertTrue(form.unifyButton.isEnabled());
            form.unifyButton.doClick();
            assertEquals(Role.EXCLUDED, form.snapshot().personFor(b).role());
            assertEquals(1, form.contributorCount());
            form.undoButton.doClick(); assertRules(initial, form.snapshot());
        });
    }

    @Test void splitAndMoveUseDropdownIdentityAndKeepMetadataValid() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            String first = initial.personFor(a).id();
            select(form, first, initial.personFor(b).id()); destination(form, first); form.unifyButton.doClick();
            form.emailField.setSelectedItem(b.email());
            form.accountField.setSelectedItem(b.login());
            AuthorshipConfig combined = form.snapshot();
            for (int row = 0; row < form.identities.getModel().getSize(); row++)
                if (form.identities.getModel().getElementAt(row).contains(b.email())) form.identityField.setSelectedIndex(row);
            form.separateButton.doClick();
            AuthorshipConfig split = form.snapshot();
            assertNotEquals(first, split.personFor(b).id());
            assertEquals(a.email(), form.emailField.getSelectedItem());
            assertEquals(a.login(), form.accountField.getSelectedItem());
            form.undoButton.doClick(); assertRules(combined, form.snapshot());
            select(form, first);
            for (int row = 0; row < form.identities.getModel().getSize(); row++)
                if (form.identities.getModel().getElementAt(row).contains(b.email())) form.identityField.setSelectedIndex(row);
            form.moveSelectedTo(initial.personFor(c).id());
            assertEquals(initial.personFor(c).id(), form.snapshot().personFor(b).id());
            form.undoButton.doClick(); assertRules(combined, form.snapshot());
        });
    }

    @Test void compactIdentitySelectorSupportsBatchMoveAndUndo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            String first = initial.personFor(a).id();
            String third = initial.personFor(c).id();
            select(form, first, initial.personFor(b).id());
            destination(form, first); form.unifyButton.doClick();
            AuthorshipConfig combined = form.snapshot();
            assertEquals(0, form.identityField.getSelectedIndex());
            assertTrue(form.moveButton.isEnabled());
            assertTrue(form.multipleIdentitiesButton.isEnabled());
            form.identities.setSelectionInterval(0, 1);
            assertEquals(-1, form.identityField.getSelectedIndex());
            assertEquals("2 selected · Change…", form.multipleIdentitiesButton.getText());
            assertTrue(form.detailTitle.getText().endsWith("(2 identities)"));
            assertEquals("· 5 Java commits", form.commitHint.getText());
            assertTrue(form.separateButton.isEnabled());
            form.moveSelectedTo(third);
            assertEquals(third, form.snapshot().personFor(a).id());
            assertEquals(third, form.snapshot().personFor(b).id());
            form.undo(); assertRules(combined, form.snapshot());
            select(form, first);
            form.identities.setSelectionInterval(0, 1);
            form.separateButton.doClick();
            assertNotEquals(form.snapshot().personFor(a).id(), form.snapshot().personFor(b).id());
            form.undo(); assertRules(combined, form.snapshot());
        });
    }

    @Test void missingAccountHasExplicitMessageAndControlsFitNarrowWidths() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorIdentity identity = new AuthorIdentity("Long original name", "long-address@example.org", "");
            AuthorshipForm form = new AuthorshipForm(List.of(new AuthorSummary(identity, 7)), new AuthorshipConfig(), null);
            select(form, form.snapshot().personFor(identity).id());
            assertFalse(form.accountField.isEnabled());
            JLabel account = (JLabel) form.accountField.getRenderer().getListCellRendererComponent(
                    new javax.swing.JList<>(), null, -1, false, false);
            assertEquals("No linked GitHub account", account.getText());
            assertEquals("Long original name (1 identity)", form.detailTitle.getText());
            assertEquals("· 7 Java commits", form.commitHint.getText());
            assertFalse(form.multipleIdentitiesButton.isEnabled());
            assertFalse(form.separateButton.isEnabled());
            // Lay out the actual detail pane at a narrow width: no horizontal scrolling.
            java.awt.Container single = form.identityField.getParent().getParent().getParent().getParent().getParent();
            single.setSize(300, 260);
            layoutTree(single);
            var scroll = (javax.swing.JScrollPane) form.identityField.getParent().getParent().getParent().getParent();
            assertEquals(javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scroll.getHorizontalScrollBarPolicy());
            assertTrue(form.identityField.getWidth() > 60);
            assertTrue(form.multipleIdentitiesButton.getWidth() <= 300);
        });
    }

    private static void layoutTree(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents())
            if (child instanceof java.awt.Container nested) layoutTree(nested);
    }

    @Test void pendingNameIsCommittedBeforeSelectionSavingRestorationAndHistory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            String first = initial.personFor(a).id();
            select(form, first);
            editName(form, "Pending name");
            // Use the real selection listener without snapshotting the old panel first.
            form.people.setRowSelectionInterval(1, 1);
            assertEquals("Pending name", form.snapshot().personFor(a).name());
            form.undoButton.doClick(); assertRules(initial, form.snapshot());
            select(form, first); editName(form, "Typed before undo");
            form.undo(); assertRules(initial, form.snapshot());
            form.redo(); assertEquals("Typed before undo", form.snapshot().personFor(a).name());
            form.restoreOriginal(); assertRules(initial, form.snapshot());
            form.undo(); assertEquals("Typed before undo", form.snapshot().personFor(a).name());
        });
    }

    @Test void blankNamesAreRejectedAndEmptyContactFieldsRemainReadOnly() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorIdentity blank = new AuthorIdentity("Name only", "", "");
            AuthorshipForm form = new AuthorshipForm(List.of(new AuthorSummary(blank, 0)), new AuthorshipConfig(), null);
            select(form, form.snapshot().personFor(blank).id());
            assertFalse(form.emailField.isEnabled()); assertFalse(form.accountField.isEnabled());
            editName(form, "  ");
            assertThrows(IllegalStateException.class, form::snapshot);
            assertEquals("Name only", form.snapshot().personFor(blank).name());
            assertEquals(0, form.contributorCount());
        });
    }

    @Test void originalRestorationSurvivesReopeningAndResetsEveryOverrideIncludingAbsentAssignments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig input = new AuthorshipConfig();
            String target = input.discover(a, Role.EXCLUDED).id();
            input.discover(b, Role.MEMBER); input.assign(b.key(), target);
            input.assign("absent-key", target);
            input.putPerson(new AuthorshipConfig.Person(target, "Custom", Role.EXCLUDED, b.email(), b.login()));
            AuthorshipForm form = new AuthorshipForm(authors, input, 4);
            AuthorshipConfig initial = form.snapshot();
            form.restoreOriginal();
            AuthorshipConfig restored = form.snapshot();
            assertEquals(3, form.people.getRowCount());
            assertEquals(3, restored.assignments().size());
            assertEquals("login", restored.personFor(a).name());
            assertEquals(Role.MEMBER, restored.personFor(a).role());
            assertEquals("", restored.personFor(a).email());
            assertEquals("4", form.teamSizeValue.getText());
            assertEquals("Declared", form.teamSizeSource.getText());
            assertEquals("25,00 %", form.expectedShareValue.getText());
            form.undo(); assertRules(initial, form.snapshot());
            form.redo(); assertRules(restored, form.snapshot());
            AuthorshipForm reopened = new AuthorshipForm(authors, initial, 4);
            reopened.restoreOriginal(); assertRules(restored, reopened.snapshot());
            assertEquals("Custom", input.personFor(a).name());
        });
    }

    @Test void untrustedNamesNeverActivateHtmlInTablesChoicesTitlesOrSummaries() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String attack = "<html><img src='http://127.0.0.1/should-not-load'>student";
            AuthorIdentity identity = new AuthorIdentity(attack, "student@example.org", "");
            AuthorshipForm form = new AuthorshipForm(List.of(new AuthorSummary(identity, 1)), new AuthorshipConfig(), null);
            select(form, form.snapshot().personFor(identity).id());
            for (JTable table : List.of(form.people)) {
                JLabel label = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
                assertEquals(attack, label.getText());
                assertNull(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            }
            @SuppressWarnings("unchecked")
            var renderer = (javax.swing.ListCellRenderer<Object>) form.nameField.getRenderer();
            JLabel label = (JLabel) renderer.getListCellRendererComponent(new javax.swing.JList<>(), attack, 0, false, false);
            assertNull(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            JLabel identityLabel = (JLabel) form.identities.getCellRenderer().getListCellRendererComponent(
                    form.identities, attack, 0, false, false);
            assertNull(identityLabel.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            assertTrue(form.detailTitle.getText().startsWith(attack));
            assertTrue(form.changeSummary().contains("Contributors:"));
        });
    }

    @Test void percentagesUseSpanishFormattingAndExcludedOrUnavailableValuesUseADash() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(List.of(), new AuthorshipConfig(), 4);
            assertEquals("25,00 %", form.expectedShareValue.getText());
            for (float value : new float[] {.25f, Float.NaN, Float.POSITIVE_INFINITY}) {
                JLabel label = (JLabel) form.people.getDefaultRenderer(Float.class)
                        .getTableCellRendererComponent(form.people, value, false, false, 0, 4);
                assertEquals(Float.isFinite(value) ? "25,00 %" : "—", label.getText());
            }
            assertEquals(0, form.people.getRowCount()); assertFalse(form.undoButton.isEnabled());
        });
    }

    private RepositorySnapshot source() {
        Commit first = new Commit("first", a, 1000, List.of(), List.of(new ChangedFile("Same.java", "", 10, 0)));
        Commit second = new Commit("second", b, 2000, List.of("first"), List.of(new ChangedFile("Same.java", "", 20, 0)));
        return new RepositorySnapshot(1, "https://github.com/course/project", "project", 3000, 1, 2000, true,
                "main", Map.of("main", "second"), List.of(first, second), List.of());
    }

    @Test void asynchronousPreviewKeepsLatestAssignmentsSelectionAndPendingTextWithoutChangingRawData() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<AuthorshipForm> view = new AtomicReference<>();
        RepositorySnapshot raw = source();
        List<Commit> original = List.copyOf(raw.commits());
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(raw, new AuthorshipConfig(), null);
            view.set(form);
            AuthorshipConfig initial = form.snapshot();
            String first = initial.personFor(a).id();
            select(form, first, initial.personFor(b).id()); destination(form, first); form.unifyButton.doClick();
            form.undo(); form.redo();
            editName(form, "Still typing");
            form.previewStatus.addPropertyChangeListener("text", event -> {
                if (event.getNewValue().toString().startsWith("Local preview ·")) completed.countDown();
            });
            assertTrue(form.previewStatus.getText().startsWith("Calculating"));
        });
        assertTrue(completed.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = view.get();
            assertEquals(1, form.people.getSelectedRowCount());
            assertEquals("Still typing", form.nameField.getEditor().getItem());
            assertEquals(2, form.people.getValueAt(0, 3));
            assertEquals(1f, form.people.getValueAt(0, 4));
            assertEquals(original, raw.commits());
        });
    }

    @Test void restorationIncludesOriginalIdentitiesOutsideTheActiveBranch() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RepositorySnapshot raw = source();
            Commit hidden = new Commit("hidden", c, 1500, List.of(), List.of(new ChangedFile("Hidden.java", "", 5, 0)));
            RepositorySnapshot branched = new RepositorySnapshot(1, raw.url(), "project", 3000, 1, 2000, true,
                    "main", Map.of("main", "second", "other", "hidden"), List.of(raw.commits().get(0), raw.commits().get(1), hidden), List.of());
            AuthorshipConfig config = new AuthorshipConfig();
            String first = config.discover(a, Role.MEMBER).id();
            config.discover(c, Role.TEACHER); config.assign(c.key(), first);
            AuthorshipForm form = new AuthorshipForm(branched, config, null);
            form.restoreOriginal();
            assertNotEquals(form.snapshot().personFor(a).id(), form.snapshot().personFor(c).id());
            assertEquals(Role.MEMBER, form.snapshot().personFor(c).role());
        });
    }
    @Test void typingImmediatelyEnablesUndoAndInvalidTextCannotBlockRestoration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            select(form, initial.personFor(a).id());
            assertFalse(form.undoButton.isEnabled());
            editName(form, "Pending text");
            assertTrue(form.undoButton.isEnabled()); assertTrue(form.discardButton.isEnabled());
            form.undoButton.doClick(); assertRules(initial, form.snapshot());
            editName(form, " ");
            form.undo(); assertRules(initial, form.snapshot());
            editName(form, " ");
            form.restoreOriginal(); assertRules(initial, form.snapshot());
        });
    }

    @Test void exportedSnapshotsCannotMutateTheEditorOrItsHistory() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig exported = form.snapshot();
            String first = exported.personFor(a).id();
            exported.putPerson(new AuthorshipConfig.Person(first, "External", Role.EXCLUDED));
            assertEquals("login", form.snapshot().personFor(a).name());
            assertEquals(Role.MEMBER, form.snapshot().personFor(a).role());
            assertFalse(form.undoButton.isEnabled());
        });
    }

    @Test void nativeNameChoicesMatchContactControlsAndCustomEditingIsExplicitAndReversible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            select(form, initial.personFor(a).id(), initial.personFor(b).id());
            destination(form, initial.personFor(a).id());
            form.unifyButton.doClick();
            assertFalse(form.nameField.isEditable());
            assertFalse(form.emailField.isEditable());
            assertFalse(form.accountField.isEditable());
            form.nameField.setSelectedItem("Alias");
            assertEquals("Alias", form.snapshot().personFor(a).name());
            form.editNameButton.doClick();
            assertTrue(form.nameField.isEditable());
            form.nameField.getEditor().setItem("Custom visible name");
            form.editNameButton.doClick();
            assertFalse(form.nameField.isEditable());
            assertEquals("Custom visible name", form.nameField.getSelectedItem());
            form.undo(); assertEquals("Alias", form.snapshot().personFor(a).name());
            form.redo(); assertEquals("Custom visible name", form.snapshot().personFor(a).name());
            assertFalse(form.nameField.isEditable());
        });
    }

    private static void pressTableKey(AuthorshipForm form, int key, int modifiers) {
        javax.swing.KeyStroke stroke = javax.swing.KeyStroke.getKeyStroke(key, modifiers);
        Object actionKey = form.people.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(stroke);
        assertNotNull(actionKey, "Missing table binding for " + stroke);
        javax.swing.Action action = form.people.getActionMap().get(actionKey);
        assertNotNull(action, "Missing action for " + stroke);
        action.actionPerformed(new java.awt.event.ActionEvent(form.people, java.awt.event.ActionEvent.ACTION_PERFORMED, actionKey.toString()));
    }

    @Test void shiftArrowSelectionActivatesCombinationAndStillRequiresDestinationConfirmation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig before = form.snapshot();
            form.people.changeSelection(0, 0, false, false);
            assertFalse(form.unifyButton.isEnabled());
            pressTableKey(form, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            assertEquals(2, form.people.getSelectedRowCount());
            assertTrue(form.unifyButton.isEnabled());
            assertEquals("Combine selected…", form.unifyButton.getText());
            form.unifyButton.doClick();
            assertRules(before, form.snapshot()); assertFalse(form.undoButton.isEnabled());
            pressTableKey(form, java.awt.event.KeyEvent.VK_DOWN, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            assertEquals(3, form.people.getSelectedRowCount()); assertTrue(form.unifyButton.isEnabled());
            pressTableKey(form, java.awt.event.KeyEvent.VK_UP, java.awt.event.InputEvent.SHIFT_DOWN_MASK);
            assertEquals(2, form.people.getSelectedRowCount());
            destination(form, before.personFor(b).id());
            assertEquals("Confirm combination", form.unifyButton.getText());
            form.unifyButton.doClick();
            assertEquals(before.personFor(b).id(), form.snapshot().personFor(a).id());
            assertEquals(2, form.people.getRowCount());
            form.undo(); assertRules(before, form.snapshot());
        });
    }

    @Test void ctrlOrCmdToggleSelectionActivatesCombinationAndDeselectingDisablesIt() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig before = form.snapshot();
            // JTable receives the same toggle flag for Ctrl-click or Cmd-click on each platform.
            form.people.changeSelection(0, 0, false, false);
            form.people.changeSelection(2, 0, true, false);
            assertEquals(2, form.people.getSelectedRowCount());
            assertTrue(form.unifyButton.isEnabled());
            assertEquals("Combine selected…", form.unifyButton.getText());
            form.unifyButton.doClick(); assertRules(before, form.snapshot());
            form.people.changeSelection(2, 0, true, false);
            assertEquals(1, form.people.getSelectedRowCount()); assertFalse(form.unifyButton.isEnabled());
            form.people.changeSelection(1, 0, true, false);
            assertEquals(2, form.people.getSelectedRowCount()); assertTrue(form.unifyButton.isEnabled());
            destination(form, before.personFor(a).id());
            form.unifyButton.doClick();
            assertEquals(before.personFor(a).id(), form.snapshot().personFor(b).id());
            form.undo(); assertRules(before, form.snapshot());
        });
    }

    @Test void documentationAndMergeOnlyAuthorsStayVisibleAndCanBeCombinedAndRestored() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Commit java = new Commit("java", a, 1000, List.of(), List.of(new ChangedFile("Example.java", "", 10, 0)));
            Commit docs = new Commit("docs", b, 2000, List.of("java"), List.of(new ChangedFile("README.md", "", 30, 0)));
            Commit merge = new Commit("merge", c, 3000, List.of("java", "docs"), List.of());
            RepositorySnapshot source = new RepositorySnapshot(1, "https://github.com/course/all-authors", "all-authors", 4000,
                    1, 3000, true, "main", Map.of("main", "merge"), List.of(java, docs, merge), List.of());
            AuthorshipForm form = new AuthorshipForm(source, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            assertEquals(3, form.people.getRowCount());
            assertEquals("3", form.identityCount.getText());
            assertEquals("3", form.peopleCount.getText());
            assertEquals("1", form.contributorCountLabel.getText());
            assertEquals("1", form.teamSizeValue.getText());
            assertEquals("Inferred", form.teamSizeSource.getText());
            assertEquals("100,00 %", form.expectedShareValue.getText());
            form.searchField.setText(b.email());
            assertEquals(1, form.people.getRowCount());
            assertEquals(0, form.people.getValueAt(0, 3));
            form.searchField.setText("");
            select(form, initial.personFor(b).id(), initial.personFor(c).id());
            destination(form, initial.personFor(b).id());
            form.unifyButton.doClick();
            assertEquals(2, form.people.getRowCount());
            assertEquals(initial.personFor(b).id(), form.snapshot().personFor(c).id());
            assertEquals("1", form.contributorCountLabel.getText());
            form.undo(); assertRules(initial, form.snapshot());
            form.redo(); assertEquals(2, form.people.getRowCount());
            form.restoreOriginal(); assertRules(initial, form.snapshot());
            assertEquals(List.of(java, docs, merge), source.commits());
        });
    }

    @Test void repositoriesWithoutJavaStillListEveryAuthorAndHaveNoInferredJavaShare() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Commit first = new Commit("first", a, 1000, List.of(), List.of(new ChangedFile("README.md", "", 10, 0)));
            Commit second = new Commit("second", b, 2000, List.of("first"), List.of(new ChangedFile("notes.txt", "", 20, 0)));
            RepositorySnapshot source = new RepositorySnapshot(1, "https://github.com/course/docs", "docs", 3000,
                    1, 2000, true, "main", Map.of("main", "second"), List.of(first, second), List.of());
            AuthorshipForm form = new AuthorshipForm(source, new AuthorshipConfig(), null);
            assertEquals(2, form.people.getRowCount());
            assertEquals("2", form.peopleCount.getText());
            assertEquals("0", form.contributorCountLabel.getText());
            assertEquals("0", form.teamSizeValue.getText());
            assertEquals("—", form.expectedShareValue.getText());
            assertFalse(form.summaryWarning.isVisible());
        });
    }

    @Test void roleLabelsAreCompactAndPreserveTeacherAndOtherExclusions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig config = new AuthorshipConfig();
            config.discover(a, Role.TEACHER); config.discover(b, Role.EXCLUDED);
            AuthorshipForm form = new AuthorshipForm(authors, config, null);
            String[] labels = {"Teacher", "Other", "Teammate"};
            for (int row = 0; row < labels.length; row++) {
                JLabel label = (JLabel) form.people.prepareRenderer(form.people.getCellRenderer(row, 1), row, 1);
                assertEquals(labels[row], label.getText());
            }
            select(form, form.snapshot().personFor(b).id());
            assertEquals(Role.EXCLUDED, form.participation.getSelectedItem());
            assertEquals(Role.EXCLUDED, form.snapshot().personFor(b).role());
            assertEquals(1, form.contributorCount());
        });
    }

}
