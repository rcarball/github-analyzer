// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */
package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

/**
 * Tests for the hardened cache deserialization: a valid cache round-trips, while
 * tampered content (disallowed classes, wrong element types, missing file) is
 * rejected safely and falls back to an empty cache instead of executing or
 * exploding.
 */
public class DataManagerTest {

    @Test
    public void refreshWithConfiguredRepositoriesNeedsAtLeastOneResultToReplaceCache() {
        assertFalse(DataManager.shouldStoreRefresh(1, List.of()));
        assertFalse(DataManager.shouldStoreRefresh(1, null));
        assertTrue(DataManager.shouldStoreRefresh(1, List.of(new RepoStats())));
        assertTrue(DataManager.shouldStoreRefresh(0, List.of()),
                "An empty repository configuration intentionally produces an empty cache");
    }

    @Test
    public void csvTextCellsEscapeSeparatorsQuotesAndFormulaPrefixes() {
        assertEquals("\"team;A\"", DataManager.csvTextCell("team;A"));
        assertEquals("\"She said \"\"hello\"\"\"", DataManager.csvTextCell("She said \"hello\""));
        assertEquals("\"first line\nsecond line\"", DataManager.csvTextCell("first line\nsecond line"));
        assertEquals("\"'=SUM(A1:A2)\"", DataManager.csvTextCell("=SUM(A1:A2)"));
        assertEquals("\"'  @command\"", DataManager.csvTextCell("  @command"));
        assertEquals("\"\"", DataManager.csvTextCell(null));
    }

    @Test
    public void csvExportUsesUtf8BomAndKeepsConflictingTextInOneCell(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats();
        repo.setGroup("team;A");
        repo.setUrl("https://github.com/owner/repo");
        repo.addUserStats(new UserStats("=SUM(A1:A2)", "a\"b@example.com", 2, 1, 10, 3, 13, -1L, -1L));

        Path file = tmp.resolve("stats.csv");
        DataManager.getInstance().storeCSV(List.of(repo), file);
        String csv = Files.readString(file);

        assertTrue(csv.startsWith("\uFEFFGROUP;URL;USERNAME;EMAIL;"));
        assertTrue(csv.contains("\"team;A\";\"https://github.com/owner/repo\";\"'=SUM(A1:A2)\";\"a\"\"b@example.com\""));
        assertTrue(csv.contains(";10;3;13;"), "Metric values must remain numeric fields");
    }

    private static void writeObject(Path file, Object obj) throws Exception {
        try (OutputStream os = Files.newOutputStream(file);
             ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(os))) {
            out.writeObject(obj);
        }
    }

    @Test
    public void roundTripsAValidCache(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats();
        repo.setUrl("https://github.com/owner/repo");
        repo.setName("repo");
        repo.setCommits(10);
        repo.setMergeCommits(2);
        repo.getFileTypeMap().put(".java", 5); // exercises TreeMap + Integer through the filter
        repo.addUserStats(new UserStats("alice", "alice@x.com", 5, 2, 100, 20, 120, 1000L, 5000L));

        List<RepoStats> data = new ArrayList<>();
        data.add(repo);

        Path file = tmp.resolve("stats.dat");
        writeObject(file, data);

        List<RepoStats> loaded = DataManager.getInstance().loadData(file);

        assertEquals(1, loaded.size());
        assertEquals("https://github.com/owner/repo", loaded.get(0).getUrl());
        assertEquals(10, loaded.get(0).getCommits());
        assertEquals(2, loaded.get(0).getMergeCommits());
        assertEquals(1, loaded.get(0).getUserStats().size());
        assertEquals(5, loaded.get(0).getFileTypeMap().get(".java"));
    }

    @Test
    public void missingFileReturnsEmpty(@TempDir Path tmp) {
        List<RepoStats> loaded = DataManager.getInstance().loadData(tmp.resolve("does-not-exist.dat"));
        assertTrue(loaded.isEmpty());
    }

    @Test
    public void rejectsDisallowedClassInStream(@TempDir Path tmp) throws Exception {
        // A tampered cache whose graph contains a class outside the allow-list
        // (java.io.File) must be blocked by the deserialization filter.
        List<Object> evil = new ArrayList<>();
        evil.add(new File("/etc/passwd"));

        Path file = tmp.resolve("stats.dat");
        writeObject(file, evil);

        List<RepoStats> loaded = DataManager.getInstance().loadData(file);
        assertTrue(loaded.isEmpty(), "Disallowed classes must be rejected, yielding an empty cache");
    }

    @Test
    public void rejectsWrongElementType(@TempDir Path tmp) throws Exception {
        // A List whose elements are allowed classes but not RepoStats must be rejected
        // before it reaches the rest of the app.
        List<Object> wrong = new ArrayList<>();
        wrong.add("not a RepoStats");

        Path file = tmp.resolve("stats.dat");
        writeObject(file, wrong);

        List<RepoStats> loaded = DataManager.getInstance().loadData(file);
        assertTrue(loaded.isEmpty(), "A list of the wrong element type must yield an empty cache");
    }

    @Test
    public void rejectsNonListRoot(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("stats.dat");
        writeObject(file, "just a string");

        List<RepoStats> loaded = DataManager.getInstance().loadData(file);
        assertTrue(loaded.isEmpty(), "A non-List root object must yield an empty cache");
    }
}
