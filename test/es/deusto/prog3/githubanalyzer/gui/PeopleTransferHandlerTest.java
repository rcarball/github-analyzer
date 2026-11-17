package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;

import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;

class PeopleTransferHandlerTest {
    private final AuthorIdentity a = new AuthorIdentity("Ana", "ana@example.org", "ana");
    private final AuthorIdentity b = new AuthorIdentity("Ana personal", "personal@example.org", "personal");
    private final AuthorIdentity c = new AuthorIdentity("Eva", "eva@example.org", "eva");
    private final AuthorIdentity d = new AuthorIdentity("Luis", "luis@example.org", "luis");
    private final List<AuthorIdentity> identities = List.of(a, b, c, d);
    private final List<AuthorSummary> authors = identities.stream().map(identity -> new AuthorSummary(identity, 2)).toList();

    private AuthorshipForm form() { return new AuthorshipForm(authors, new AuthorshipConfig(), 4); }

    private static PeopleTransferHandler handler(AuthorshipForm form) {
        return assertInstanceOf(PeopleTransferHandler.class, form.people.getTransferHandler());
    }

    private int row(AuthorshipForm form, AuthorIdentity identity) {
        AuthorshipConfig config = form.snapshot();
        List<String> active = identities.stream().map(value -> config.personFor(value).id()).distinct().toList();
        return form.people.convertRowIndexToView(active.indexOf(config.personFor(identity).id()));
    }

    private void select(AuthorshipForm form, AuthorIdentity... selected) {
        int[] rows = java.util.Arrays.stream(selected).mapToInt(identity -> row(form, identity)).toArray();
        form.people.getSelectionModel().setValueIsAdjusting(true);
        form.people.clearSelection();
        for (int row : rows) form.people.addRowSelectionInterval(row, row);
        form.people.getSelectionModel().setValueIsAdjusting(false);
    }

    private static void editName(AuthorshipForm form, String name) {
        if (!form.nameField.isEditable()) form.editNameButton.doClick();
        form.nameField.getEditor().setItem(name);
    }

    private static void assertRules(AuthorshipConfig expected, AuthorshipConfig actual) {
        assertEquals(expected.assignments(), actual.assignments());
        assertEquals(expected.people(), actual.people());
    }

    @Test void droppingPreselectsTheDestinationWithoutEditingAndConfirmationIsOneUndoableOperation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            AuthorshipConfig before = form.snapshot();
            select(form, b);
            PeopleTransferHandler handler = handler(form);
            assertEquals(DropMode.ON, form.people.getDropMode());
            assertEquals(TransferHandler.MOVE, handler.getSourceActions(form.people));
            Transferable drag = handler.createTransferable(form.people);
            assertTrue(handler.canDrop(drag, row(form, a)));
            assertTrue(handler.prepareDrop(drag, row(form, a)));
            assertRules(before, form.snapshot());
            assertEquals(before.personFor(a).id(), ((AuthorshipForm.PersonChoice) form.mergeTarget.getSelectedItem()).id());
            assertTrue(form.unifyButton.isEnabled()); assertFalse(form.undoButton.isEnabled());
            // Completing MOVE must not remove rows before the user confirms.
            handler.exportDone(form.people, drag, TransferHandler.MOVE);
            assertRules(before, form.snapshot()); assertEquals(4, form.people.getRowCount());
            form.unifyButton.doClick();
            AuthorshipConfig combined = form.snapshot();
            assertEquals(combined.personFor(a).id(), combined.personFor(b).id());
            assertEquals(3, form.people.getRowCount());
            form.undoButton.doClick(); assertRules(before, form.snapshot());
            assertFalse(form.undoButton.isEnabled());
            form.redoButton.doClick(); assertRules(combined, form.snapshot());
        });
    }

    @Test void multipleSourcesAndIncrementalGroupDragsRetainSavedAbsentAliases() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig input = new AuthorshipConfig();
            String first = input.discover(a, Role.MEMBER).id();
            input.assign("temporarily-absent", first);
            AuthorshipForm form = new AuthorshipForm(authors, input, 4);
            AuthorshipConfig before = form.snapshot();
            select(form, a, b);
            PeopleTransferHandler handler = handler(form);
            Transferable firstDrag = handler.createTransferable(form.people);
            assertTrue(handler.prepareDrop(firstDrag, row(form, c)));
            assertEquals(3, form.people.getSelectedRowCount());
            form.unifyButton.doClick();
            AuthorshipConfig combined = form.snapshot();
            String target = combined.personFor(c).id();
            assertEquals(target, combined.assignments().get("temporarily-absent"));
            assertEquals(2, form.people.getRowCount());
            select(form, c);
            Transferable nextDrag = handler.createTransferable(form.people);
            assertTrue(handler.prepareDrop(nextDrag, row(form, d)));
            form.unifyButton.doClick();
            AuthorshipConfig all = form.snapshot();
            assertEquals(1, form.people.getRowCount());
            assertEquals(1, all.assignments().values().stream().distinct().count());
            form.undo(); assertRules(combined, form.snapshot());
            form.undo(); assertRules(before, form.snapshot());
            form.redo(); assertRules(combined, form.snapshot());
            form.redo(); assertRules(all, form.snapshot());
            form.restoreOriginal();
            assertEquals(4, form.people.getRowCount());
            assertFalse(form.snapshot().assignments().containsKey("temporarily-absent"));
            form.undo(); assertRules(all, form.snapshot());
        });
    }

    @Test void dragCapturesIdsAndResolvesTheTargetAfterSortingFilteringAndColumnReordering() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            select(form, b);
            PeopleTransferHandler handler = handler(form);
            Transferable drag = handler.createTransferable(form.people);
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.getRowSorter().toggleSortOrder(0);
            form.people.moveColumn(0, 4);
            // Both Ana aliases remain visible, while unrelated rows disappear.
            form.searchField.setText("Ana");
            assertEquals(2, form.people.getRowCount());
            assertTrue(handler.prepareDrop(drag, row(form, a)));
            form.unifyButton.doClick();
            AuthorshipConfig combined = form.snapshot();
            assertEquals(combined.personFor(a).id(), combined.personFor(b).id());
            assertNotEquals(combined.personFor(a).id(), combined.personFor(c).id());
            form.undo();
            form.searchField.setText("");
            assertEquals(4, form.people.getRowCount());
        });
    }

    @Test void cancelingAProposalOrTheNativeGestureDoesNotEditOrClearRedo() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            AuthorshipConfig before = form.snapshot();
            PeopleTransferHandler handler = handler(form);
            select(form, a);
            Transferable drag = handler.createTransferable(form.people);
            assertTrue(handler.prepareDrop(drag, row(form, b)));
            form.unifyButton.doClick();
            form.undo(); assertTrue(form.redoButton.isEnabled());
            select(form, a);
            Transferable canceled = handler.createTransferable(form.people);
            handler.exportDone(form.people, canceled, TransferHandler.NONE);
            assertFalse(handler.prepareDrop(canceled, row(form, b)));
            assertRules(before, form.snapshot()); assertTrue(form.redoButton.isEnabled());
            Transferable proposal = handler.createTransferable(form.people);
            assertTrue(handler.prepareDrop(proposal, row(form, b)));
            form.cancelCombineButton.doClick();
            assertRules(before, form.snapshot()); assertTrue(form.redoButton.isEnabled());
            assertEquals(1, form.people.getSelectedRowCount());
            form.redoButton.doClick(); assertEquals(3, form.people.getRowCount());
        });
    }

    @Test void mixedRolesRequireAChoiceEvenWhenTheDropSuppliesTheDestination() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig input = new AuthorshipConfig();
            input.discover(b, Role.TEACHER);
            AuthorshipForm form = new AuthorshipForm(authors, input, 4);
            AuthorshipConfig before = form.snapshot();
            select(form, a);
            assertTrue(handler(form).prepareDrop(handler(form).createTransferable(form.people), row(form, b)));
            assertNull(form.mergeRole.getSelectedItem()); assertTrue(form.unifyButton.isEnabled());
            assertEquals("Combine selected…", form.unifyButton.getText());
            form.unifyButton.doClick();
            assertRules(before, form.snapshot());
            form.mergeRole.setSelectedItem(Role.MEMBER);
            form.unifyButton.doClick();
            assertEquals(Role.MEMBER, form.snapshot().personFor(b).role());
            form.undo(); assertRules(before, form.snapshot());
        });
    }

    @Test void rejectedSelfEmptyHiddenAndForeignDropsLeaveTheDraftAndSelectionIntact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            AuthorshipConfig before = form.snapshot();
            select(form, a, b);
            PeopleTransferHandler handler = handler(form);
            Transferable drag = handler.createTransferable(form.people);
            assertFalse(handler.prepareDrop(drag, row(form, a)));
            assertFalse(handler.prepareDrop(drag, row(form, b)));
            assertFalse(handler.prepareDrop(drag, -1));
            assertFalse(handler.prepareDrop(drag, form.people.getRowCount()));
            assertFalse(handler.prepareDrop(new StringSelection("ana"), row(form, c)));
            AuthorshipForm other = form();
            select(other, a);
            assertFalse(handler.prepareDrop(handler(other).createTransferable(other.people), row(form, c)));
            assertRules(before, form.snapshot()); assertEquals(2, form.people.getSelectedRowCount());
            form.searchField.setText("eva");
            assertFalse(handler.prepareDrop(drag, row(form, c)));
            assertRules(before, form.snapshot()); assertFalse(form.undoButton.isEnabled());
        });
    }

    @Test void draftChangesExpireTheDragEvenIfUndoReturnsToTheSameAssignments() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            select(form, a);
            PeopleTransferHandler handler = handler(form);
            Transferable drag = handler.createTransferable(form.people);
            form.participation.setSelectedItem(Role.EXCLUDED);
            assertFalse(handler.prepareDrop(drag, row(form, b)));
            form.undo();
            assertFalse(handler.prepareDrop(drag, row(form, b)));
            assertFalse(form.undoButton.isEnabled());
        });
    }

    @Test void noSelectionClipboardAndMalformedPayloadsAreRejected() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            PeopleTransferHandler handler = handler(form);
            assertEquals(TransferHandler.NONE, handler.getSourceActions(form.people));
            assertNull(handler.createTransferable(form.people));
            assertNull(handler.createTransferable(new JComponent() { private static final long serialVersionUID = 1L; }));
            select(form, a);
            Transferable drag = handler.createTransferable(form.people);
            TransferHandler.TransferSupport paste = new TransferHandler.TransferSupport(form.people, drag);
            assertFalse(handler.canImport(paste)); assertFalse(handler.importData(paste));
            Transferable broken = new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() { return drag.getTransferDataFlavors(); }
                @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return true; }
                @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                    throw new IOException("Simulated unreadable payload");
                }
            };
            assertFalse(handler.prepareDrop(broken, row(form, b)));
            assertFalse(form.undoButton.isEnabled());
        });
    }

    @Test void pendingNameIsCommittedBeforeCapturingTheDragAndUndoKeepsThatEditSeparate() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            AuthorshipConfig initial = form.snapshot();
            select(form, a);
            editName(form, "Renamed before drag");
            Transferable drag = handler(form).createTransferable(form.people);
            AuthorshipConfig renamed = form.snapshot();
            assertEquals("Renamed before drag", renamed.personFor(a).name());
            assertTrue(handler(form).prepareDrop(drag, row(form, b)));
            form.unifyButton.doClick();
            form.undo(); assertRules(renamed, form.snapshot());
            form.undo(); assertRules(initial, form.snapshot());
        });
    }

    @Test void duplicateDisplayNamesDoNotIdentifyTheDropTargetOrLeakHtml() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipConfig input = new AuthorshipConfig();
            String attack = "<html><img src='http://127.0.0.1/should-not-load'>same";
            String first = input.discover(a, Role.MEMBER).id(), second = input.discover(b, Role.MEMBER).id();
            input.putPerson(new AuthorshipConfig.Person(first, attack, Role.MEMBER));
            input.putPerson(new AuthorshipConfig.Person(second, attack, Role.MEMBER));
            AuthorshipForm form = new AuthorshipForm(authors, input, 4);
            select(form, a);
            Transferable drag = handler(form).createTransferable(form.people);
            assertTrue(handler(form).prepareDrop(drag, row(form, b)));
            assertEquals(second, ((AuthorshipForm.PersonChoice) form.mergeTarget.getSelectedItem()).id());
            @SuppressWarnings("unchecked")
            var renderer = (javax.swing.ListCellRenderer<Object>) form.mergeTarget.getRenderer();
            var label = (javax.swing.JLabel) renderer.getListCellRendererComponent(new javax.swing.JList<>(),
                    form.mergeTarget.getSelectedItem(), 0, false, false);
            assertNull(label.getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
            form.unifyButton.doClick();
            assertEquals(second, form.snapshot().personFor(a).id());
        });
    }
    @Test void aTransferCannotBeReplayedAndSelectionChangesDoNotReplaceItsCapturedSources() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            select(form, a);
            PeopleTransferHandler handler = handler(form);
            Transferable drag = handler.createTransferable(form.people);
            select(form, d);
            assertTrue(handler.prepareDrop(drag, row(form, b)));
            assertFalse(handler.prepareDrop(drag, row(form, c)));
            form.unifyButton.doClick();
            AuthorshipConfig combined = form.snapshot();
            assertEquals(combined.personFor(a).id(), combined.personFor(b).id());
            assertNotEquals(combined.personFor(d).id(), combined.personFor(b).id());
        });
    }

    @Test void pendingEditsAfterDragStartAreNotDiscardedByADrop() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AuthorshipForm form = form();
            select(form, a);
            Transferable drag = handler(form).createTransferable(form.people);
            int targetRow = row(form, b);
            editName(form, "Typed after drag began");
            assertFalse(handler(form).prepareDrop(drag, targetRow));
            assertEquals("Typed after drag began", form.nameField.getEditor().getItem());
            assertEquals(4, form.people.getRowCount());
            assertEquals("Typed after drag began", form.snapshot().personFor(a).name());
        });
    }

}
