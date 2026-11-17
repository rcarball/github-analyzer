package es.deusto.prog3.githubanalyzer.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;

/** Atomic, reversible edits of repository-local assignments. Never mutates its input. */
final class AuthorshipDraft {
    private record Edit(String label, AuthorshipConfig before, AuthorshipConfig after) { }
    private final AuthorshipConfig initial;
    private final List<AuthorIdentity> originals;
    private final Deque<Edit> undo = new ArrayDeque<>();
    private final Deque<Edit> redo = new ArrayDeque<>();
    private AuthorshipConfig current;

    AuthorshipDraft(AuthorshipConfig saved, List<AuthorIdentity> originals) {
        this.originals = List.copyOf(originals);
        current = saved.copy();
        for (AuthorIdentity identity : originals)
            current.discover(identity, ContributionMetrics.defaultRole(identity));
        initial = current.copy();
    }

    AuthorshipConfig snapshot() { return current.copy(); }
    AuthorshipConfig initial() { return initial.copy(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }
    String undoLabel() { return canUndo() ? undo.peek().label() : ""; }
    String redoLabel() { return canRedo() ? redo.peek().label() : ""; }
    boolean isDirty() { return !same(current, initial); }

    private static boolean same(AuthorshipConfig a, AuthorshipConfig b) {
        return a.people().equals(b.people()) && a.assignments().equals(b.assignments());
    }

    private void edit(String label, Consumer<AuthorshipConfig> operation) {
        AuthorshipConfig next = current.copy();
        operation.accept(next);
        replace(label, next);
    }

    private void replace(String label, AuthorshipConfig next) {
        if (same(current, next)) return;
        undo.push(new Edit(label, current.copy(), next.copy()));
        redo.clear();
        current = next;
    }

    void undo() {
        if (!canUndo()) return;
        Edit edit = undo.pop();
        current = edit.before().copy();
        redo.push(edit);
    }

    void redo() {
        if (!canRedo()) return;
        Edit edit = redo.pop();
        current = edit.after().copy();
        undo.push(edit);
    }

    void updatePerson(Person person) {
        edit("Edit person", next -> {
            Person before = next.people().get(person.id());
            if (before == null) throw new IllegalArgumentException("Unknown person.");
            if (!person.email().equals(before.email())) validateContact(next, person.id(), person.email(), true);
            if (!person.login().equals(before.login())) validateContact(next, person.id(), person.login(), false);
            next.putPerson(person);
        });
    }

    private void validateContact(AuthorshipConfig config, String personId, String value, boolean email) {
        if (value.isEmpty()) return;
        boolean available = originals.stream().filter(identity -> personId.equals(config.personFor(identity).id()))
                .map(identity -> email ? identity.email() : identity.login()).anyMatch(value::equals);
        if (!available) throw new IllegalArgumentException("Choose contact details from this person's identities.");
    }

    void merge(Set<String> people, String targetId, Role role) {
        Set<String> selected = Set.copyOf(people);
        if (selected.size() < 2 || !selected.contains(targetId) || role == null)
            throw new IllegalArgumentException("Select at least two people, a destination and participation.");
        edit("Combine people", next -> {
            if (!next.people().keySet().containsAll(selected)) throw new IllegalArgumentException("Unknown person.");
            Person target = next.people().get(targetId);
            next.putPerson(new Person(target.id(), target.name(), role, target.email(), target.login()));
            // Include saved aliases that are absent from the current analysis.
            next.assignments().forEach((identity, person) -> {
                if (selected.contains(person)) next.assign(identity, targetId);
            });
        });
    }

    void move(List<String> identityKeys, String targetId) {
        if (identityKeys.isEmpty()) return;
        edit("Move identities", next -> {
            if (!next.people().containsKey(targetId)) throw new IllegalArgumentException("Unknown destination.");
            Set<String> affected = new LinkedHashSet<>();
            for (String key : identityKeys) {
                String previous = next.assignments().get(key);
                if (previous == null) throw new IllegalArgumentException("Unknown identity.");
                if (!previous.equals(targetId)) affected.add(previous);
                next.assign(key, targetId);
            }
            normalizeContacts(next, affected);
        });
    }

    void separate(List<String> identityKeys) {
        Set<String> selected = new LinkedHashSet<>(identityKeys);
        edit("Separate identities", next -> {
            Set<String> affected = new LinkedHashSet<>();
            for (String key : selected) {
                AuthorIdentity identity = originals.stream().filter(value -> value.key().equals(key)).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unknown original identity."));
                Person previous = next.personFor(identity);
                long count = next.assignments().values().stream().filter(previous.id()::equals).count();
                if (count <= 1) continue;
                affected.add(previous.id());
                Person person = new Person(UUID.randomUUID().toString(), identity.displayName(), previous.role());
                next.putPerson(person);
                next.assign(key, person.id());
            }
            normalizeContacts(next, affected);
        });
    }

    private void normalizeContacts(AuthorshipConfig next, Set<String> affected) {
        for (String id : affected) {
            Person person = next.people().get(id);
            List<AuthorIdentity> group = originals.stream()
                    .filter(identity -> Objects.equals(next.assignments().get(identity.key()), person.id())).toList();
            Set<String> knownKeys = originals.stream().map(AuthorIdentity::key).collect(java.util.stream.Collectors.toSet());
            boolean hasAbsentAlias = next.assignments().entrySet().stream()
                    .anyMatch(entry -> person.id().equals(entry.getValue()) && !knownKeys.contains(entry.getKey()));
            if (hasAbsentAlias) continue;
            // Empty means automatic selection; do not retain another group's explicit contact.
            String email = group.stream().map(AuthorIdentity::email).anyMatch(person.email()::equals) ? person.email() : "";
            String login = group.stream().map(AuthorIdentity::login).anyMatch(person.login()::equals) ? person.login() : "";
            next.putPerson(new Person(person.id(), person.name(), person.role(), email, login));
        }
    }

    void discardSession() { replace("Restore session start", initial.copy()); }

    /** Drop every saved rule, then rebuild separate identities from the original download. */
    void restoreOriginal() {
        AuthorshipConfig restored = new AuthorshipConfig();
        for (AuthorIdentity identity : originals)
            restored.discover(identity, ContributionMetrics.defaultRole(identity));
        replace("Restore original identities", restored);
    }
}
