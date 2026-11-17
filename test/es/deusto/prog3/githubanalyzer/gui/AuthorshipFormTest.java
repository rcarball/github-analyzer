package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;

class AuthorshipFormTest {
    @Test void observedRendererAndExpectedSummaryUseTheSamePercentageStyle() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(List.of(), new AuthorshipConfig(), 4);
            assertTrue(form.summary.getText().contains("25,00 %"));
            var renderer = form.people.getDefaultRenderer(Float.class);
            var label = (javax.swing.JLabel) renderer.getTableCellRendererComponent(form.people, .25f, false, false, 0, 5);
            assertEquals("25,00 %", label.getText());
            label = (javax.swing.JLabel) renderer.getTableCellRendererComponent(form.people, Float.NaN, false, false, 0, 5);
            assertEquals("—", label.getText());
            label = (javax.swing.JLabel) renderer.getTableCellRendererComponent(form.people, Float.POSITIVE_INFINITY, false, false, 0, 5);
            assertEquals("—", label.getText());
        });
    }
    private final AuthorIdentity a = new AuthorIdentity("Name", "school@example.org", "login");
    private final AuthorIdentity b = new AuthorIdentity("Alias", "home@example.org", "login");
    private final List<AuthorSummary> authors = List.of(new AuthorSummary(a, 3), new AuthorSummary(b, 2));

    private static List<String> options(JComboBox<?> combo) {
        return java.util.stream.IntStream.range(0, combo.getItemCount()).mapToObj(i -> combo.getItemAt(i).toString()).toList();
    }
    private static JComboBox<?> editPerson(AuthorshipForm form, int row, int modelColumn) {
        assertTrue(form.people.editCellAt(row, form.people.convertColumnIndexToView(modelColumn)));
        return assertInstanceOf(JComboBox.class, form.people.getEditorComponent());
    }

    @Test void groupedMetadataUsesDistinctNonEmptyChoicesAndSurvivesReopeningWithoutChangingTheInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorIdentity third = new AuthorIdentity("Third name", "", "second-login");
            AuthorIdentity blank = new AuthorIdentity("Blank account", "", "");
            AuthorIdentity other = new AuthorIdentity("Other person", "other@example.org", "other-login");
            AuthorshipConfig original = new AuthorshipConfig();
            List<AuthorSummary> discovered = List.of(authors.get(0), authors.get(1), new AuthorSummary(third, 1),
                    new AuthorSummary(blank, 0), new AuthorSummary(other, 1));
            AuthorshipForm form = new AuthorshipForm(discovered, original, null);
            String id = form.snapshot().personFor(a).id();
            for (int row : new int[] {1, 2, 3}) form.assign(row, id);
            assertEquals(List.of("Visible name", "Email", "GitHub account", "Participation", "Java commits", "Observed share"),
                    java.util.stream.IntStream.range(0, 6).mapToObj(form.people::getColumnName).toList());
            JComboBox<?> names = editPerson(form, 0, 0);
            assertTrue(options(names).containsAll(List.of("Name", "Alias", "Third name")));
            assertFalse(options(names).contains("Other person"));
            names.setSelectedItem("Alias"); form.snapshot();
            JComboBox<?> emails = editPerson(form, 0, 1);
            assertFalse(emails.isEditable()); assertEquals(List.of(a.email(), b.email()), options(emails));
            emails.setSelectedItem(b.email()); form.snapshot();
            JComboBox<?> accounts = editPerson(form, 0, 2);
            assertFalse(accounts.isEditable()); assertEquals(List.of("login", "second-login"), options(accounts));
            accounts.setSelectedItem("second-login");
            AuthorshipConfig saved = form.snapshot();
            assertEquals("Alias", saved.personFor(a).name()); assertEquals(b.email(), saved.personFor(a).email());
            assertEquals("second-login", saved.personFor(a).login()); assertEquals(2, form.contributorCount());
            assertTrue(form.changeSummary().contains("Email:")); assertTrue(form.changeSummary().contains("second-login"));
            AuthorshipForm reopened = new AuthorshipForm(discovered, saved, null);
            assertEquals("Alias", reopened.people.getModel().getValueAt(0, 0));
            assertEquals(b.email(), reopened.people.getModel().getValueAt(0, 1));
            assertEquals("second-login", reopened.people.getModel().getValueAt(0, 2));
            assertTrue(original.assignments().isEmpty()); assertEquals(a.email(), form.identities.getModel().getValueAt(0, 1));
        });
    }
    @Test void metadataAndRoleEditorsStillWorkAfterReorderingEveryLowerTableColumn() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int column = 0; column < 4; column++) for (int position = 0; position < 6; position++) {
                AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
                form.assign(1, form.snapshot().personFor(a).id());
                form.people.moveColumn(column, position);
                Object value = switch (column) { case 0 -> "Alias"; case 1 -> b.email(); case 2 -> "login"; default -> Role.EXCLUDED; };
                editPerson(form, 0, column).setSelectedItem(value);
                AuthorshipConfig.Person saved = form.snapshot().personFor(a);
                assertEquals(value, switch (column) { case 0 -> saved.name(); case 1 -> saved.email(); case 2 -> saved.login(); default -> saved.role(); });
            }
        });
    }
    @Test void splitUsesOnlyRemainingMetadataAndRestoreResetsTheSelections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorIdentity alias = new AuthorIdentity("Alias", b.email(), "other-login");
            List<AuthorSummary> discovered = List.of(authors.get(0), new AuthorSummary(alias, 2));
            AuthorshipForm form = new AuthorshipForm(discovered, new AuthorshipConfig(), null);
            form.assign(1, form.snapshot().personFor(a).id());
            editPerson(form, 0, 1).setSelectedItem(alias.email()); form.snapshot();
            editPerson(form, 0, 2).setSelectedItem(alias.login());
            form.separate(1); // Commit a pending selection before changing the group.
            assertEquals(a.email(), form.people.getModel().getValueAt(0, 1));
            assertEquals(a.login(), form.people.getModel().getValueAt(0, 2));
            assertEquals(alias.email(), form.people.getModel().getValueAt(1, 1));
            assertEquals(List.of(a.login()), options(editPerson(form, 0, 2))); form.snapshot();
            form.restoreOriginal();
            assertEquals("", form.snapshot().personFor(a).email()); assertEquals("", form.snapshot().personFor(a).login());
            assertEquals(a.email(), form.people.getModel().getValueAt(0, 1));
        });
    }
    @Test void emptyMetadataIsReadOnlyAndRenameAndParticipationKeepChosenContactValues() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorIdentity blank = new AuthorIdentity("Name only", "", "");
            AuthorshipForm empty = new AuthorshipForm(List.of(new AuthorSummary(blank, 1)), new AuthorshipConfig(), null);
            assertFalse(empty.people.isCellEditable(0, 1)); assertFalse(empty.people.isCellEditable(0, 2));
            assertEquals("", empty.people.getValueAt(0, 1)); assertEquals("", empty.people.getValueAt(0, 2));
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            form.assign(1, form.snapshot().personFor(a).id());
            editPerson(form, 0, 1).setSelectedItem(b.email()); form.snapshot();
            editPerson(form, 0, 2).setSelectedItem(a.login()); form.snapshot();
            form.people.getModel().setValueAt("Custom name", 0, 0);
            form.people.getModel().setValueAt(Role.TEACHER, 0, 3);
            AuthorshipConfig.Person person = form.snapshot().personFor(a);
            assertEquals("Custom name", person.name()); assertEquals(b.email(), person.email()); assertEquals(a.login(), person.login());
            assertEquals(Role.TEACHER, person.role());
            form.people.getModel().setValueAt("foreign@example.org", 0, 1);
            form.people.getModel().setValueAt("foreign-login", 0, 2);
            assertEquals(person, form.snapshot().personFor(a));
        });
    }

    @Test void mergeSplitRenameAndRoleChangesUpdateCountsWithoutTouchingOriginal() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig original = new AuthorshipConfig();
            AuthorshipForm form = new AuthorshipForm(authors, original, null);
            assertEquals(2, form.contributorCount());
            String first = form.snapshot().personFor(a).id();
            form.assign(1, first);
            assertEquals(1, form.contributorCount());
            assertTrue(form.summary.getText().contains("100,00 %"));
            form.people.getModel().setValueAt("Renamed", 0, 0);
            assertEquals("Renamed", form.snapshot().personFor(a).name());
            form.people.getModel().setValueAt(Role.EXCLUDED, 0, 3);
            assertEquals(0, form.contributorCount());
            assertTrue(form.changeSummary().contains("Other account"));
            form.separate(1);
            assertNotEquals(form.snapshot().personFor(a).id(), form.snapshot().personFor(b).id());
            assertEquals(2, form.people.getRowCount());
            assertTrue(original.assignments().isEmpty(), "Closing/cancelling must leave the input configuration intact");
        });
    }
    @Test void realComboBoxEditingMergesAndSeparatesWithoutReentrantEditorFailures() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            assertTrue(form.identities.editCellAt(1, 4));
            JComboBox<?> editor = (JComboBox<?>) form.identities.getEditorComponent();
            editor.setSelectedIndex(0);
            form.snapshot();
            assertEquals(1, form.contributorCount());
            assertTrue(form.identities.editCellAt(1, 4));
            editor = (JComboBox<?>) form.identities.getEditorComponent();
            editor.setSelectedIndex(editor.getItemCount() - 1);
            form.snapshot();
            assertEquals(2, form.contributorCount());
        });
    }
    @Test void pendingNameEditsAreCommittedBeforeSavingAndBlankNamesAreRejected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            assertTrue(form.people.editCellAt(0, 0));
            ((JComboBox<?>) form.people.getEditorComponent()).getEditor().setItem("Saved name");
            assertEquals("Saved name", form.snapshot().personFor(a).name());
            form.people.getModel().setValueAt(" ", 0, 0);
            assertEquals("Saved name", form.snapshot().personFor(a).name());
        });
    }
    @Test void declaredSizeSurvivesMergesAndDocsOnlyAuthorsDoNotInflateInferredSize() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), 4);
            form.assign(1, form.snapshot().personFor(a).id());
            assertEquals(1, form.contributorCount());
            assertTrue(form.summary.getText().contains("4 (declared)"));
            assertTrue(form.summary.getText().contains("25,00 %"));
            assertTrue(form.summary.getText().contains("differs"));
            AuthorshipForm docsOnly = new AuthorshipForm(List.of(new AuthorSummary(a, 0)), new AuthorshipConfig(), null);
            assertEquals(0, docsOnly.contributorCount());
            assertTrue(docsOnly.summary.getText().contains("0 (inferred)"));
            AuthorshipForm empty = new AuthorshipForm(List.of(), new AuthorshipConfig(), null);
            assertEquals(0, empty.people.getRowCount());
            assertEquals(0, empty.contributorCount());
        });
    }
    @Test void existingAssignmentsForAbsentAliasesAreRetainedAndCanReceiveNewIdentities() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig rules = new AuthorshipConfig();
            rules.putPerson(new AuthorshipConfig.Person("saved-person", "Known person", Role.MEMBER));
            rules.assign(new AuthorIdentity("Old alias", "old@example.org", "").key(), "saved-person");
            AuthorshipForm form = new AuthorshipForm(authors, rules, null);
            assertTrue(form.identities.editCellAt(0, 4));
            JComboBox<?> editor = (JComboBox<?>) form.identities.getEditorComponent();
            int target = -1;
            for (int i = 0; i < editor.getItemCount(); i++) if (editor.getItemAt(i).toString().contains("Known person")) target = i;
            assertTrue(target >= 0);
            editor.setSelectedIndex(target);
            assertEquals("saved-person", form.snapshot().personFor(a).id());
            assertEquals(3, form.snapshot().assignments().size());
        });
    }
    @Test void editingAPersonThenMergingItsRowDoesNotCrashOrLoseTheDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            AuthorshipConfig initial = form.snapshot();
            String firstId = initial.personFor(a).id();
            String secondId = initial.personFor(b).id();
            assertTrue(form.people.editCellAt(1, 0));
            ((JComboBox<?>) form.people.getEditorComponent()).getEditor().setItem("Renamed second person");
            assertTrue(form.identities.editCellAt(1, 4));
            assertFalse(form.people.isEditing(), "Commit the old person row before changing assignments");
            JComboBox<?> editor = (JComboBox<?>) form.identities.getEditorComponent();
            editor.setSelectedIndex(0);
            AuthorshipConfig saved = assertDoesNotThrow(form::snapshot);
            assertEquals(firstId, saved.personFor(b).id());
            assertEquals("Renamed second person", saved.people().get(secondId).name());
            assertEquals(1, form.contributorCount());
            assertDoesNotThrow(form::changeSummary);
        });
    }
    @Test void directMergeCommitsPendingPersonEditBeforeRemovingItsRow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            String firstId = form.snapshot().personFor(a).id();
            assertTrue(form.people.editCellAt(1, 0));
            ((JComboBox<?>) form.people.getEditorComponent()).getEditor().setItem("Pending name");
            form.assign(1, firstId);
            assertDoesNotThrow(form::snapshot);
            assertFalse(form.people.isEditing());
            assertEquals(1, form.people.getRowCount());
        });
    }
    @Test void aSplitAlsoCommitsPendingEditsBeforeReorderingPeople() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
            String originalId = form.snapshot().personFor(a).id();
            form.assign(1, originalId);
            assertTrue(form.people.editCellAt(0, 0));
            ((JComboBox<?>) form.people.getEditorComponent()).getEditor().setItem("Kept name");
            form.separate(0);
            AuthorshipConfig saved = assertDoesNotThrow(form::snapshot);
            assertEquals("Kept name", saved.people().get(originalId).name());
            assertEquals("Kept name", saved.personFor(b).name());
            assertNotEquals(originalId, saved.personFor(a).id());
            assertEquals(2, form.people.getRowCount());
        });
    }
    @Test void personEditorMergesAndSplitsAfterMovingTheColumnToAnyPosition() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int position = 0; position < 5; position++) {
                AuthorshipForm form = new AuthorshipForm(authors, new AuthorshipConfig(), null);
                assertTrue(form.identities.getTableHeader().getReorderingAllowed());
                form.identities.moveColumn(4, position);
                int personColumn = form.identities.convertColumnIndexToView(4);
                assertEquals(position, personColumn);
                assertEquals("Person", form.identities.getColumnName(personColumn));
                assertTrue(form.identities.editCellAt(1, personColumn), "Person editor at view position " + position);
                JComboBox<?> editor = assertInstanceOf(JComboBox.class, form.identities.getEditorComponent());
                editor.setSelectedIndex(0);
                AuthorshipConfig merged = form.snapshot();
                assertEquals(merged.personFor(a).id(), merged.personFor(b).id());
                assertEquals(1, form.contributorCount());
                assertTrue(form.identities.editCellAt(1, personColumn));
                editor = assertInstanceOf(JComboBox.class, form.identities.getEditorComponent());
                editor.setSelectedIndex(editor.getItemCount() - 1);
                AuthorshipConfig split = form.snapshot();
                assertNotEquals(split.personFor(a).id(), split.personFor(b).id());
                assertEquals(2, form.contributorCount());
                assertEquals(b.email(), form.identities.getModel().getValueAt(1, 1));
            }
        });
    }
    @Test void visibleActionsUnifySeparateAndRestoreOriginalDistributionWithoutChangingRawData() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit("first", a, 1000, List.of(), List.of(new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile("Same.java", "", 10, 0)));
            var second = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit("second", b, 2000, List.of("first"), List.of(new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile("Same.java", "", 20, 0)));
            var raw = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot(1, "https://github.com/course/project", "project", 3000, 1, 2000, true, "main", java.util.Map.of("main", "second"), List.of(first, second), List.of());
            AuthorshipConfig original = new AuthorshipConfig();
            AuthorshipForm form = new AuthorshipForm(raw, original, null);
            assertFalse(form.unifyButton.isEnabled());
            form.identities.setRowSelectionInterval(0, 1);
            assertTrue(form.unifyButton.isEnabled()); form.unifyButton.doClick();
            assertEquals(1, form.contributorCount());
            form.people.setRowSelectionInterval(0, 0);
            assertEquals(2, form.identities.getSelectedRowCount()); assertTrue(form.separateButton.isEnabled());
            form.separateButton.doClick(); assertEquals(2, form.contributorCount());
            form.identities.setRowSelectionInterval(0, 1); form.unifyButton.doClick();
            form.restoreButton.doClick(); assertEquals(2, form.contributorCount());
            assertNotEquals(form.snapshot().personFor(a).id(), form.snapshot().personFor(b).id());
            assertTrue(original.assignments().isEmpty()); assertEquals(List.of(first, second), raw.commits());
        });
    }

    @Test void untrustedNamesAndChoicesNeverActivateSwingHtml() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String attack = "<html><img src='http://127.0.0.1/should-not-load'>student";
            AuthorIdentity identity = new AuthorIdentity(attack, "student@example.org", "");
            AuthorshipForm form = new AuthorshipForm(List.of(new AuthorSummary(identity, 1)), new AuthorshipConfig(), null);
            for (javax.swing.JTable table : List.of(form.identities, form.people)) {
                javax.swing.JLabel label = (javax.swing.JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
                assertEquals(attack, label.getText());
                assertNull(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            }
            JComboBox<?> combo = editPerson(form, 0, 0);
            @SuppressWarnings("unchecked")
            javax.swing.ListCellRenderer<Object> renderer = (javax.swing.ListCellRenderer<Object>) combo.getRenderer();
            javax.swing.JLabel choice = (javax.swing.JLabel) renderer.getListCellRendererComponent(new javax.swing.JList<>(), attack, 0, false, false);
            assertNull(choice.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
        });
    }


    @Test void asynchronousPreviewUsesLatestAssignmentsAndKeepsEdtResponsive() throws Exception {
        var completed = new java.util.concurrent.CountDownLatch(1);
        var view = new java.util.concurrent.atomic.AtomicReference<AuthorshipForm>();
        SwingUtilities.invokeAndWait(() -> {
            var first = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit("first", a, 1000, List.of(), List.of(new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile("Same.java", "", 10, 0)));
            var second = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit("second", b, 2000, List.of("first"), List.of(new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile("Same.java", "", 20, 0)));
            var raw = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot(1, "https://github.com/course/project", "project", 3000, 1, 2000, true, "main", java.util.Map.of("main", "second"), List.of(first, second), List.of());
            AuthorshipForm form = new AuthorshipForm(raw, new AuthorshipConfig(), null);
            view.set(form);
            form.identities.setRowSelectionInterval(0, 1); form.unifySelected();
            form.separateSelected();
            form.identities.setRowSelectionInterval(0, 1); form.unifySelected();
            form.previewStatus.addPropertyChangeListener("text", event -> {
                if (event.getNewValue().toString().startsWith("Local preview ·")) completed.countDown();
            });
            form.people.setRowSelectionInterval(0, 0);
            assertTrue(form.people.editCellAt(0, 0));
            assertTrue(form.previewStatus.getText().startsWith("Calculating"));
        });
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(1, view.get().contributorCount());
            assertEquals(0, view.get().people.getSelectedRow());
            assertTrue(view.get().people.isEditing(), "Preview completion must not interrupt a name edit");
            assertEquals(2, view.get().people.getValueAt(0, 4));
            assertEquals(1f, view.get().people.getValueAt(0, 5));
        });
    }
}
