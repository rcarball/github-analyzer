package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;

class AuthorshipStoreTest {
    private static final String URL = "https://github.com/course/project";
    @Test void roundTripsExplicitAssignmentsNamesAndRolesWithoutChangingOtherRepositories(@TempDir Path dir) throws Exception {
        AuthorshipStore store = new AuthorshipStore(dir);
        AuthorIdentity a = new AuthorIdentity("Name", "school@example.org", "login");
        AuthorIdentity b = new AuthorIdentity("Alias", "home@example.org", "login");
        AuthorshipConfig config = new AuthorshipConfig();
        config.putPerson(new Person("stable-id", "José \\ teacher\nname", Role.TEACHER, b.email(), b.login()));
        config.assign(a.key(), "stable-id"); config.assign(b.key(), "stable-id");
        store.save(URL, config);
        byte[] before = Files.readAllBytes(store.pathFor(URL));
        AuthorshipConfig loaded = store.load(URL);
        assertEquals(config.people(), loaded.people());
        assertEquals(config.assignments(), loaded.assignments());
        assertEquals(loaded.personFor(a), loaded.personFor(b));
        assertTrue(store.load(URL + "-other").assignments().isEmpty());
        store.save(URL + "-other", new AuthorshipConfig());
        assertArrayEquals(before, Files.readAllBytes(store.pathFor(URL)));
    }
    @Test void legacySettingsWithoutContactSelectionsRemainReadableAndAreNotRewritten(@TempDir Path dir) throws Exception {
        AuthorshipStore store = new AuthorshipStore(dir);
        AuthorIdentity identity = new AuthorIdentity("Name", "mail@example.org", "login");
        String text = "version=1\nrepository=" + URL + "\nperson.id.name=Custom name\nperson.id.role=MEMBER\nidentity." + identity.key() + "=id\n";
        Files.writeString(store.pathFor(URL), text);
        Person loaded = store.load(URL).personFor(identity);
        assertEquals("Custom name", loaded.name()); assertEquals("", loaded.email()); assertEquals("", loaded.login());
        assertEquals(identity.email(), loaded.emailFor(java.util.List.of(identity)));
        assertEquals(identity.login(), loaded.loginFor(java.util.List.of(identity)));
        assertEquals(text, Files.readString(store.pathFor(URL)));
    }
    @Test void invalidOrWrongRepositorySettingsFailClosed(@TempDir Path dir) throws Exception {
        AuthorshipStore store = new AuthorshipStore(dir);
        for (String text : new String[] {"version=1\nrepository=wrong", "version=2\nrepository=" + URL,
                "version=1\nrepository=" + URL + "\nidentity.author=missing-person",
                "version=1\nrepository=" + URL + "\nperson.a.name=Name\nperson.a.role=INVALID"}) {
            Files.writeString(store.pathFor(URL), text);
            assertThrows(IllegalStateException.class, () -> store.load(URL));
            assertEquals(text, Files.readString(store.pathFor(URL)));
        }
    }
    @Test void defaultDiscoveryNeverMergesSimilarIdentitiesAndExistingRoleWins() {
        AuthorshipConfig config = new AuthorshipConfig();
        AuthorIdentity a = new AuthorIdentity("Same", "same@school.example", "same-login");
        AuthorIdentity b = new AuthorIdentity("Same", "same@home.example", "same-login");
        Person one = config.discover(a, Role.MEMBER), two = config.discover(b, Role.MEMBER);
        assertNotEquals(one.id(), two.id());
        config.putPerson(new Person(one.id(), "Renamed teacher", Role.TEACHER));
        config.assign(b.key(), one.id());
        assertEquals(Role.TEACHER, config.discover(b, Role.MEMBER).role());
    }
    @Test void draftCopiesDoNotMutateOriginalAndRejectDanglingAssignments() {
        AuthorshipConfig original = new AuthorshipConfig();
        AuthorIdentity identity = new AuthorIdentity("Name", "email@example.org", null);
        original.discover(identity, Role.MEMBER);
        AuthorshipConfig draft = original.copy();
        draft.putPerson(new Person("new", "Other", Role.EXCLUDED));
        draft.assign(identity.key(), "new");
        assertNotEquals(original.personFor(identity), draft.personFor(identity));
        assertThrows(IllegalArgumentException.class, () -> draft.assign(identity.key(), "missing"));
        assertThrows(IllegalArgumentException.class, () -> new Person("id", " ", Role.MEMBER));
    }
    @Test void originalIdentityKeysAreStableAndDoNotUseFuzzyNormalization() {
        assertEquals(new AuthorIdentity("Name", "MAIL@EXAMPLE.ORG", "Login").key(), new AuthorIdentity(" name ", "mail@example.org", "login").key());
        assertNotEquals(new AuthorIdentity("José", "mail@example.org", "login").key(), new AuthorIdentity("Jose", "mail@example.org", "login").key());
        assertNotEquals(new AuthorIdentity("a:b", "c", "").key(), new AuthorIdentity("a", "b:c", "").key());
    }
    @Test void failedSaveDoesNotOverwriteAnExistingConfiguration(@TempDir Path dir) throws Exception {
        Path blocked = dir.resolve("file"); Files.writeString(blocked, "keep");
        assertThrows(IllegalStateException.class, () -> new AuthorshipStore(blocked).save(URL, new AuthorshipConfig()));
        assertEquals("keep", Files.readString(blocked));
    }
}
