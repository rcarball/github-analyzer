package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;

class AuthorshipDraftTest {
    private final AuthorIdentity a = new AuthorIdentity("Ana", "school@example.org", "ana");
    private final AuthorIdentity b = new AuthorIdentity("Alias", "home@example.org", "personal");
    private final AuthorIdentity c = new AuthorIdentity("Third", "third@example.org", "");
    private final List<AuthorIdentity> identities = List.of(a, b, c);

    private static void assertRules(AuthorshipConfig expected, AuthorshipConfig actual) {
        assertEquals(expected.assignments(), actual.assignments());
        assertEquals(expected.people(), actual.people());
    }

    @Test void incrementalMergesUndoAndRedoEveryExactStateWithoutTouchingInput() {
        AuthorshipConfig saved = new AuthorshipConfig();
        AuthorshipDraft draft = new AuthorshipDraft(saved, identities);
        AuthorshipConfig initial = draft.snapshot();
        String first = initial.personFor(a).id();
        draft.merge(Set.of(first, initial.personFor(b).id()), first, Role.MEMBER);
        AuthorshipConfig combined = draft.snapshot();
        draft.merge(Set.of(first, initial.personFor(c).id()), first, Role.TEACHER);
        AuthorshipConfig all = draft.snapshot();
        assertEquals(Set.of(first), Set.copyOf(all.assignments().values()));
        assertEquals(Role.TEACHER, all.personFor(c).role());
        draft.undo(); assertRules(combined, draft.snapshot());
        draft.undo(); assertRules(initial, draft.snapshot());
        assertFalse(draft.isDirty()); assertFalse(draft.canUndo());
        draft.redo(); assertRules(combined, draft.snapshot());
        draft.redo(); assertRules(all, draft.snapshot());
        assertTrue(saved.assignments().isEmpty());
        assertTrue(saved.people().isEmpty());
    }

    @Test void reopeningCombinedGroupsThenMergingAlsoMovesAbsentSavedAliases() {
        AuthorshipDraft first = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig initial = first.snapshot();
        String target = initial.personFor(a).id();
        first.merge(Set.of(target, initial.personFor(b).id()), target, Role.MEMBER);
        AuthorshipConfig saved = first.snapshot();
        AuthorIdentity absent = new AuthorIdentity("Absent", "absent@example.org", "");
        saved.assign(absent.key(), target);
        AuthorshipDraft reopened = new AuthorshipDraft(saved, identities);
        String destination = saved.personFor(c).id();
        reopened.merge(Set.of(target, destination), destination, Role.EXCLUDED);
        assertEquals(4, reopened.snapshot().assignments().size());
        assertEquals(Set.of(destination), Set.copyOf(reopened.snapshot().assignments().values()));
        reopened.undo(); assertRules(saved, reopened.snapshot());
    }

    @Test void splitNormalizesContactsAndUndoRestoresExplicitChoicesAndGeneratedIds() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig initial = draft.snapshot();
        String target = initial.personFor(a).id();
        draft.merge(Set.of(target, initial.personFor(b).id()), target, Role.MEMBER);
        draft.updatePerson(new Person(target, "Custom", Role.TEACHER, b.email(), b.login()));
        AuthorshipConfig before = draft.snapshot();
        draft.separate(List.of(b.key()));
        AuthorshipConfig split = draft.snapshot();
        assertNotEquals(target, split.personFor(b).id());
        assertEquals(Role.TEACHER, split.personFor(b).role());
        assertEquals("Custom", split.personFor(a).name());
        assertEquals(a.email(), split.personFor(a).emailFor(List.of(a)));
        assertEquals("", split.personFor(a).email());
        draft.undo(); assertRules(before, draft.snapshot());
        draft.redo(); assertRules(split, draft.snapshot());
    }

    @Test void fullRestorationDropsAllOverridesAndAbsentRulesAndIsReversibleAfterReopening() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig original = draft.snapshot();
        String target = original.personFor(a).id();
        draft.merge(Set.copyOf(original.assignments().values()), target, Role.EXCLUDED);
        draft.updatePerson(new Person(target, "Custom", Role.EXCLUDED, b.email(), b.login()));
        AuthorshipConfig saved = draft.snapshot();
        saved.assign("absent-identity-key", target);
        AuthorshipDraft reopened = new AuthorshipDraft(saved, identities);
        reopened.restoreOriginal();
        assertRules(original, reopened.snapshot());
        assertFalse(reopened.snapshot().assignments().containsKey("absent-identity-key"));
        reopened.undo(); assertRules(saved, reopened.snapshot());
        reopened.redo(); assertRules(original, reopened.snapshot());
    }

    @Test void sessionRestoreRecoversSavedGroupsAndCanItselfBeUndone() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig initial = draft.snapshot();
        String target = initial.personFor(a).id();
        draft.merge(Set.of(target, initial.personFor(b).id()), target, Role.MEMBER);
        AuthorshipDraft reopened = new AuthorshipDraft(draft.snapshot(), identities);
        AuthorshipConfig saved = reopened.snapshot();
        reopened.restoreOriginal();
        AuthorshipConfig restored = reopened.snapshot();
        reopened.discardSession();
        assertRules(saved, reopened.snapshot()); assertFalse(reopened.isDirty());
        reopened.undo(); assertRules(restored, reopened.snapshot());
        reopened.redo(); assertRules(saved, reopened.snapshot());
    }

    @Test void invalidOperationIsAtomicAndKeepsRedoAndNoOpDoesNotCreateHistory() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig initial = draft.snapshot();
        draft.separate(List.of(a.key())); assertFalse(draft.canUndo());
        draft.updatePerson(initial.personFor(a)); assertFalse(draft.canUndo());
        String first = initial.personFor(a).id();
        draft.move(List.of(b.key()), first); draft.undo();
        assertTrue(draft.canRedo());
        assertThrows(IllegalArgumentException.class, () -> draft.move(List.of(b.key(), "unknown"), first));
        assertRules(initial, draft.snapshot()); assertTrue(draft.canRedo());
        assertThrows(IllegalArgumentException.class, () -> draft.merge(Set.of(first, initial.personFor(b).id()), first, null));
        assertRules(initial, draft.snapshot());
        draft.updatePerson(new Person(first, "Changed", Role.MEMBER));
        assertFalse(draft.canRedo());
    }

    @Test void contactValidationAndSnapshotIsolationProtectTheDraft() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        AuthorshipConfig initial = draft.snapshot();
        String first = initial.personFor(a).id();
        assertThrows(IllegalArgumentException.class, () -> draft.updatePerson(new Person(first, "Name", Role.MEMBER, b.email(), "")));
        assertRules(initial, draft.snapshot());
        initial.putPerson(new Person(first, "External change", Role.EXCLUDED));
        assertEquals("ana", draft.snapshot().personFor(a).name());
        assertFalse(draft.canUndo());
    }

    @Test void repeatedRestorationAndEmptyRepositoryAreNoOps() {
        AuthorshipDraft empty = new AuthorshipDraft(new AuthorshipConfig(), List.of());
        empty.restoreOriginal(); empty.discardSession(); empty.undo(); empty.redo();
        assertFalse(empty.canUndo()); assertFalse(empty.canRedo());
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        draft.restoreOriginal(); assertFalse(draft.canUndo());
    }
    @Test void unrelatedAbsentPeopleKeepTheirSavedContactOverridesDuringSplitAndMove() {
        AuthorshipConfig saved = new AuthorshipConfig();
        saved.putPerson(new Person("absent", "Absent", Role.TEACHER, "absent@example.org", "absent-login"));
        saved.assign("absent-key", "absent");
        AuthorshipDraft draft = new AuthorshipDraft(saved, identities);
        AuthorshipConfig initial = draft.snapshot();
        String target = initial.personFor(a).id();
        draft.move(List.of(b.key()), target);
        draft.separate(List.of(b.key()));
        assertEquals(saved.people().get("absent"), draft.snapshot().people().get("absent"));
        assertEquals("absent", draft.snapshot().assignments().get("absent-key"));
    }

    @Test void longMixedSequenceCanBeReplayedExactlyInBothDirections() {
        AuthorshipDraft draft = new AuthorshipDraft(new AuthorshipConfig(), identities);
        java.util.Random random = new java.util.Random(47);
        java.util.List<AuthorshipConfig> states = new java.util.ArrayList<>();
        states.add(draft.snapshot());
        for (int index = 0; index < 120; index++) {
            AuthorshipConfig before = draft.snapshot();
            List<String> people = before.assignments().values().stream().distinct().sorted().toList();
            String target = people.get(random.nextInt(people.size()));
            switch (random.nextInt(6)) {
                case 0 -> { if (people.size() > 1) draft.merge(Set.copyOf(people), target, Role.MEMBER); }
                case 1 -> draft.separate(List.of(identities.get(random.nextInt(identities.size())).key()));
                case 2 -> draft.move(List.of(identities.get(random.nextInt(identities.size())).key()), target);
                case 3 -> draft.updatePerson(new Person(target, "Name " + index, Role.values()[index % 3]));
                case 4 -> draft.restoreOriginal();
                default -> draft.discardSession();
            }
            AuthorshipConfig after = draft.snapshot();
            assertEquals(identities.size(), after.assignments().size());
            assertTrue(after.people().keySet().containsAll(after.assignments().values()));
            if (!before.assignments().equals(after.assignments()) || !before.people().equals(after.people())) states.add(after);
        }
        for (int index = states.size() - 2; index >= 0; index--) {
            draft.undo(); assertRules(states.get(index), draft.snapshot());
        }
        assertFalse(draft.canUndo());
        for (int index = 1; index < states.size(); index++) {
            draft.redo(); assertRules(states.get(index), draft.snapshot());
        }
        assertFalse(draft.canRedo());
    }

    @Test void absentContactPreferencesDoNotBlockRenamingAndUnchangedMovesAreNoOps() {
        AuthorshipConfig saved = new AuthorshipConfig();
        String first = saved.discover(a, Role.MEMBER).id();
        saved.assign("absent-key", first);
        saved.putPerson(new Person(first, "Known", Role.MEMBER, "absent@example.org", "absent-login"));
        AuthorshipDraft draft = new AuthorshipDraft(saved, identities);
        draft.move(List.of(a.key()), first);
        assertFalse(draft.canUndo());
        draft.updatePerson(new Person(first, "Renamed", Role.TEACHER, "absent@example.org", "absent-login"));
        assertEquals("Renamed", draft.snapshot().personFor(a).name());
        assertEquals("absent@example.org", draft.snapshot().personFor(a).email());
        draft.separate(List.of(a.key()));
        assertEquals("absent@example.org", draft.snapshot().people().get(first).email());
    }

}
