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

package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import es.deusto.prog3.githubanalyzer.domain.*;

class RawDataStoreTest {
    private static final String URL = "https://github.com/course/project";
    private static RepositorySnapshot snapshot() {
        var commit = new RepositorySnapshot.Commit("sha", new AuthorIdentity("Name", "email@example.org", "login"), 1000,
                List.of(), List.of(new RepositorySnapshot.ChangedFile("src/Test.java", "old/Test.java", 10, 2)));
        return new RepositorySnapshot(1, URL, "project", 2000, 1, 1000, true, "main", Map.of("main", "sha"),
                List.of(commit), List.of(new RepositorySnapshot.SourceFile("src/Test.java", "// IAG\nclass Test {}\n", "blob")));
    }
    @Test void roundTripsOriginalIdentityParentsFilesAndSourceText(@TempDir Path dir) {
        RawDataStore store = new RawDataStore(dir); RepositorySnapshot original = snapshot(); store.save(original);
        assertEquals(original, store.load(URL));
        assertThrows(UnsupportedOperationException.class, () -> original.commits().clear());
        assertThrows(UnsupportedOperationException.class, () -> original.commits().get(0).files().clear());
    }
    @Test void rejectsLegacyStatisticsWrongRepositoriesAndUnexpectedTypes(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir);
        for (Object value : List.of(new ArrayList<>(List.of(new RepoStats())), new java.io.File("/tmp/test"), "wrong")) {
            try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(store.pathFor(URL)))) { out.writeObject(value); }
            assertThrows(IllegalStateException.class, () -> store.load(URL));
        }
        store.save(snapshot());
        Files.copy(store.pathFor(URL), store.pathFor(URL + "-other"));
        assertThrows(IllegalStateException.class, () -> store.load(URL + "-other"));
    }
    @Test void incompleteHistoryAndUnsupportedVersionCannotReplacePreviousDownload(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir); store.save(snapshot()); byte[] original = Files.readAllBytes(store.pathFor(URL));
        var missing = new RepositorySnapshot(1, URL, "project", 3000, 1, 1000, true, "main", Map.of("main", "missing"), List.of(), List.of());
        assertThrows(IllegalStateException.class, () -> store.save(missing));
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        assertThrows(IllegalArgumentException.class, () -> new RepositorySnapshot(99, URL, "project", 3000, 1, 1000, true, "main", Map.of(), List.of(), List.of()));
    }
    @Test void missingOrTruncatedDownloadsNeverBecomeEmptyStatistics(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir); assertNull(store.load(URL));
        Files.write(store.pathFor(URL), new byte[] {1, 2, 3});
        assertThrows(IllegalStateException.class, () -> store.load(URL));
    }
    @Test void ordinaryReadsNeverCreateRecoveryCopiesButExplicitUpdateDoes(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir); byte[] damaged = {9, 8, 7}; Files.write(store.pathFor(URL), damaged);
        assertThrows(IllegalStateException.class, () -> store.load(URL)); assertFalse(Files.exists(dir.resolve("recovery")));
        assertNull(store.loadForUpdate(URL));
        assertArrayEquals(damaged, Files.readAllBytes(store.pathFor(URL)));
        try (var paths = Files.list(dir.resolve("recovery"))) {
            var copies = paths.toList(); assertEquals(1, copies.size());
            assertTrue(copies.get(0).getFileName().toString().contains(".damaged-"));
            assertArrayEquals(damaged, Files.readAllBytes(copies.get(0)));
        }
    }
    @Test void validAndMissingOriginalsNeedNoRecoveryCopy(@TempDir Path dir) {
        RawDataStore store = new RawDataStore(dir);
        assertNull(store.loadForUpdate(URL)); assertFalse(Files.exists(dir.resolve("recovery")));
        RepositorySnapshot original = snapshot(); store.save(original);
        assertEquals(original, store.loadForUpdate(URL)); assertFalse(Files.exists(dir.resolve("recovery")));
    }
    @Test void wrongFormatOriginalsAreAlsoPreservedBeforeReconstruction(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir);
        try (ObjectOutputStream output = new ObjectOutputStream(Files.newOutputStream(store.pathFor(URL)))) { output.writeObject("unsupported raw-data format"); }
        byte[] original = Files.readAllBytes(store.pathFor(URL)); assertNull(store.loadForUpdate(URL));
        try (var paths = Files.list(dir.resolve("recovery"))) { assertArrayEquals(original, Files.readAllBytes(paths.findFirst().orElseThrow())); }
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
    }

    @Test void originalFiveFieldCommitStreamsRemainReadable() throws Exception {
        // Serialized with the original five-component Commit record (serialVersionUID=1).
        String legacy = "rO0ABXNyAD9lcy5kZXVzdG8ucHJvZzMuZ2l0aHViYW5hbHl6ZXIuZG9tYWluLlJlcG9zaXRvcnlTbmFwc2hvdCRDb21taXQAAAAAAAAAAQIABUoACXRpbWVzdGFtcEwABmF1dGhvcnQANkxlcy9kZXVzdG8vcHJvZzMvZ2l0aHViYW5hbHl6ZXIvZG9tYWluL0F1dGhvcklkZW50aXR5O0wABWZpbGVzdAAQTGphdmEvdXRpbC9MaXN0O0wAB3BhcmVudHNxAH4AAkwAA3NoYXQAEkxqYXZhL2xhbmcvU3RyaW5nO3hwAAAAAAAABNJzcgA0ZXMuZGV1c3RvLnByb2czLmdpdGh1YmFuYWx5emVyLmRvbWFpbi5BdXRob3JJZGVudGl0eQAAAAAAAAABAgADTAAFZW1haWxxAH4AA0wABWxvZ2lucQB+AANMAARuYW1lcQB+AAN4cHQAEG1haWxAZXhhbXBsZS5vcmd0AAVsb2dpbnQABE5hbWVzcgARamF2YS51dGlsLkNvbGxTZXJXjqu2OhuoEQMAAUkAA3RhZ3hwAAAAAXcEAAAAAHhxAH4AC3QABmxlZ2FjeQ==";
        try (var in = new ObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(legacy)))) {
            var commit = (RepositorySnapshot.Commit) in.readObject();
            assertEquals("legacy", commit.sha()); assertEquals(1234, commit.timestamp());
            assertNull(commit.authorTimestamp()); assertEquals(1234, commit.activityTimestamp());
        }
    }

    @Test void authorAndCommitterDatesRoundTripIndependently(@TempDir Path dir) {
        RepositorySnapshot previous = snapshot();
        var commit = previous.commits().get(0);
        var dated = new RepositorySnapshot.Commit(commit.sha(), commit.author(), commit.timestamp(), commit.parents(), commit.files(), 500L);
        var source = new RepositorySnapshot(1, URL, "project", 2000, 1, 1000, true, "main", Map.of("main", "sha"), List.of(dated), previous.sourceFiles());
        RawDataStore store = new RawDataStore(dir); store.save(source);
        assertEquals(source, store.load(URL));
        assertEquals(500, store.load(URL).commits().get(0).activityTimestamp());
        assertEquals(1000, store.load(URL).commits().get(0).timestamp());
    }
}
