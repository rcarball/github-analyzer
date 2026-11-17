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
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

/**
 * Tests for the hardened cache deserialization: a valid cache round-trips, while
 * tampered content (disallowed classes, wrong element types, missing file) is
 * rejected safely and falls back to an empty cache instead of executing or
 * exploding.
 */
@ResourceLock(Resources.LOCALE)
public class DataManagerTest {

    @Test void spanishCsvFractionsRemainNumericWithAnyMachineLocale(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats(); repo.setGroup("G-Á"); repo.setTeamSize(4); repo.setAuthorCount(3);
        long date = java.time.LocalDate.of(2026, 11, 17).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        UserStats first = new UserStats("José", "jose@example.org", 1, 1, 1, 0, 1, date, date);
        UserStats second = new UserStats("Nuria", "nuria@example.org", 1, 1, 2, 0, 2, date, date);
        UserStats teacher = new UserStats("Teacher", "teacher@example.org", 1, 1, 100, 0, 100, date, date);
        first.setPerson("first", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        second.setPerson("second", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        teacher.setPerson("teacher", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.TEACHER);
        repo.setUserStats(List.of(first, second, teacher));
        Locale original = Locale.getDefault(), format = Locale.getDefault(Locale.Category.FORMAT), display = Locale.getDefault(Locale.Category.DISPLAY);
        byte[] reference = null;
        try {
            for (Locale locale : new Locale[] {Locale.US, Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("th-TH")}) {
                Locale.setDefault(locale);
                Path file = tmp.resolve("stats.csv"); DataManager.getInstance().storeCSV(List.of(repo), file);
                byte[] bytes = Files.readAllBytes(file);
                if (reference == null) reference = bytes;
                else org.junit.jupiter.api.Assertions.assertArrayEquals(reference, bytes, "Locale must not alter the CSV bytes");
                assertEquals(0xEF, bytes[0] & 0xFF); assertEquals(0xBB, bytes[1] & 0xFF); assertEquals(0xBF, bytes[2] & 0xFF);
                String csv = Files.readString(file);
                assertTrue(csv.contains("José")); assertTrue(csv.endsWith("\r\n"));
                assertFalse(csv.replace("\r\n", "").contains("\n"), "Records use CRLF on every OS");
                String[] rows = csv.substring(1).split("\r\n");
                assertEquals(4, rows.length);
                for (int i = 1; i <= 3; i++) {
                    String[] cells = rows[i].split(";", -1); assertEquals(19, cells.length);
                    assertEquals("2026/11/17", cells[10]); assertEquals("2026/11/17", cells[11]);
                    assertEquals("0,250000", cells[16]);
                    assertEquals(.25, parseSpanish(cells[16]), 0.000001);
                    if (i < 3) {
                        assertTrue(cells[7].matches("[0-9]+,[0-9]{6}"), "Fraction stays unquoted and has no percent sign");
                        assertEquals(i / 3.0, parseSpanish(cells[7]), 0.000001);
                    } else assertEquals("-", cells[7], "Teacher remains outside the observed-share model");
                }
            }
        } finally {
            Locale.setDefault(original); Locale.setDefault(Locale.Category.FORMAT, format); Locale.setDefault(Locale.Category.DISPLAY, display);
        }
    }

    private static double parseSpanish(String text) {
        java.text.ParsePosition position = new java.text.ParsePosition(0);
        Number parsed = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).parse(text, position);
        org.junit.jupiter.api.Assertions.assertNotNull(parsed);
        assertEquals(text.length(), position.getIndex(), "The entire field must parse as one Spanish number");
        return parsed.doubleValue();
    }

    @Test void zeroActivityExportsZeroShareAndUndefinedExpectedShare(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats(); UserStats member = new UserStats("member", "member@example.org", 0, 0, 0, 0, 0, -1, -1);
        member.setPerson("member", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER); repo.addUserStats(member);
        Path file = tmp.resolve("stats.csv"); DataManager.getInstance().storeCSV(List.of(repo), file);
        String[] cells = Files.readAllLines(file).get(1).split(";", -1);
        assertEquals("0,000000", cells[7]); assertEquals("", cells[12]); assertEquals("0", cells[15]); assertEquals("-", cells[16]);
    }

    @Test
    public void failedAtomicSaveReportsTheErrorAndKeepsExistingData(@TempDir Path dir) throws Exception {
        DataManager manager = DataManager.getInstance();
        Path file = dir.resolve("stats.dat");
        RepoStats repo = new RepoStats(); repo.setName("Saved");
        manager.storeData(List.of(repo), file);
        assertEquals("Saved", manager.loadData(file).get(0).getName());
        byte[] original = Files.readAllBytes(file);
        // A directory at the temporary path prevents writing, before replacement.
        Files.createDirectory(dir.resolve("stats.dat.tmp"));
        Files.writeString(dir.resolve("stats.dat.tmp/blocker"), "keep");
        IllegalStateException error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> manager.storeData(List.of(), file));
        assertTrue(error.getMessage().contains(file.toString()));
        org.junit.jupiter.api.Assertions.assertNotNull(error.getCause());
        org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(file));
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
    @Test
    public void csvIncludesTeamSizeIdentitiesRolesAndTheSameSharesAsTheGui(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats(); repo.setTeamSize(4); repo.setAuthorCount(3);
        UserStats member = new UserStats("student", "student@example.org", 2, 1, 50, 0, 50, -1, -1);
        member.setPerson("student-id", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        UserStats bot = new UserStats("bot", "bot@example.org", 3, 1, 500, 0, 500, -1, -1);
        bot.setPerson("bot-id", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.EXCLUDED);
        repo.addUserStats(member); repo.addUserStats(bot);
        Path file = tmp.resolve("stats.csv"); DataManager.getInstance().storeCSV(List.of(repo), file);
        List<String> lines = Files.readAllLines(file);
        assertTrue(lines.get(0).endsWith("TEAM_SIZE_DECLARED;CONTRIBUTORS;AUTHOR_IDENTITIES;TEAM_SIZE_EFFECTIVE;EXPECTED_SHARE;PERSON_ID;PARTICIPATION"));
        assertTrue(lines.get(1).contains(";50;0;50;1,000000;"));
        assertTrue(lines.get(1).endsWith(";4;1;3;4;0,250000;\"student-id\";\"MEMBER\""));
        assertTrue(lines.get(2).contains(";500;0;500;-;"));
        assertTrue(lines.get(2).endsWith(";\"EXCLUDED\""));
    }

    @Test
    public void serializableResultsRetainStablePeopleAndDeclaredTeamMetadata(@TempDir Path tmp) throws Exception {
        RepoStats repo = new RepoStats(); repo.setTeamSize(4); repo.setAuthorCount(3); repo.setAnalysisComplete(true);
        assertEquals(0, repo.getFileSnapshotVersion());
        repo.setFileSnapshotVersion(4);
        UserStats user = new UserStats("renamed", "mail@example.org", 1, 1, 3, 0, 3, 0, 0);
        user.setPerson("stable", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.TEACHER); repo.addUserStats(user);
        Path file = tmp.resolve("stats.dat"); writeObject(file, new ArrayList<>(List.of(repo)));
        RepoStats loaded = DataManager.getInstance().loadData(file).get(0);
        assertEquals(4, loaded.getTeamSize()); assertEquals(3, loaded.getAuthorCount()); assertTrue(loaded.isAnalysisComplete());
        assertEquals(4, loaded.getFileSnapshotVersion());
        assertEquals("stable", loaded.getUserStats().get(0).getPersonId());
        assertEquals(user.getParticipationRole(), loaded.getUserStats().get(0).getParticipationRole());
    }
}
