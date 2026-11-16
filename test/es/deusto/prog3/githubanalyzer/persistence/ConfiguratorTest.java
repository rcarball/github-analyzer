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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.Test;

public class ConfiguratorTest {

    @Test
    public void repositoryListDistinguishesUtf8ContentFromAnExplicitlyEmptyFile(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        String text = "https://github.com/course/team;Grupo-Á\n";
        config.saveRepositories(text);
        assertEquals(java.util.List.of(text.strip()), config.getRepositories());
        assertEquals(text, config.readRepositoriesText());

        config.saveRepositories("");
        assertTrue(config.getRepositories().isEmpty());
        assertEquals("", config.readRepositoriesText());
    }

    @Test
    public void missingRepositoryListIsNotTreatedAsEmptyEvenAfterRestart(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        Files.delete(repositories);
        Path cache = Path.of(config.getStatsFile());
        byte[] previousCache = {1, 2, 3};
        Files.write(cache, previousCache);

        Configurator restarted = new Configurator(dir);
        assertFalse(Files.exists(repositories), "Startup must not recreate a missing list as an empty one");
        assertThrows(IllegalStateException.class, restarted::getRepositories);
        assertThrows(IllegalStateException.class, restarted::readRepositoriesText);
        assertArrayEquals(previousCache, Files.readAllBytes(cache));
    }

    @Test
    public void unreadableOrInvalidRepositoryListIsReported(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        Files.delete(repositories);
        Files.createDirectory(repositories);
        assertThrows(IllegalStateException.class, config::getRepositories);
        assertThrows(IllegalStateException.class, config::readRepositoriesText);

        Files.delete(repositories);
        byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
        Files.write(repositories, invalidUtf8);
        assertThrows(IllegalStateException.class, config::getRepositories);
        assertThrows(IllegalStateException.class, config::readRepositoriesText);
        assertArrayEquals(invalidUtf8, Files.readAllBytes(repositories));
    }

    @Test
    public void failedConfigurationSaveKeepsPreviousMemoryAndCanBeRetried(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        config.save("previous-user", "previous-token", false, "previous-teacher", "previous@example.org");
        Path destination = dir.resolve("resources/config.properties");
        Files.delete(destination);
        Files.createDirectory(destination);

        assertThrows(IllegalStateException.class,
                () -> config.save("next-user", "next-token", true, "next-teacher", "next@example.org"));
        assertEquals("previous-user", config.getGithubUser());
        assertEquals("previous-token", config.getGithubToken());
        assertFalse(config.isLoadFromGithub());
        assertEquals("previous-teacher", config.getTeacherUser());
        assertEquals("previous@example.org", config.getTeacherEmail());

        Files.delete(destination);
        config.save("next-user", "next-token", true, "next-teacher", "next@example.org");
        Configurator restarted = new Configurator(dir);
        assertEquals("next-user", restarted.getGithubUser());
        assertEquals("next-token", restarted.getGithubToken());
        assertTrue(restarted.isLoadFromGithub());
        assertEquals("next-teacher", restarted.getTeacherUser());
        assertEquals("next@example.org", restarted.getTeacherEmail());
    }

    @Test
    public void repositorySaveFailureIsReportedAndCanBeRetried(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path destination = Path.of(config.getRepositoriesFile());
        Files.delete(destination);
        Files.createDirectory(destination);
        assertThrows(IllegalStateException.class, () -> config.saveRepositories("https://github.com/course/team\n"));

        Files.delete(destination);
        config.saveRepositories("https://github.com/course/team\n");
        assertEquals(java.util.List.of("https://github.com/course/team"), config.getRepositories());
    }

    @Test
    public void pathToPersistKeepsCustomRelativeAndAbsolutePaths() {
        Properties properties = new Properties();
        properties.setProperty("repositories.file", "course-data/repos.txt");
        properties.setProperty("stats.file", "/tmp/github-analyzer/stats.dat");

        assertEquals("course-data/repos.txt",
                Configurator.pathToPersist(properties, "repositories.file", "resources/repositories.txt"));
        assertEquals("/tmp/github-analyzer/stats.dat",
                Configurator.pathToPersist(properties, "stats.file", "resources/stats.dat"));
    }

    @Test
    public void pathToPersistUsesDefaultOnlyWhenThePropertyIsMissing() {
        Properties properties = new Properties();
        properties.setProperty("stats.csv", "");

        assertEquals("resources/repositories.txt",
                Configurator.pathToPersist(properties, "repositories.file", "resources/repositories.txt"));
        assertEquals("", Configurator.pathToPersist(properties, "stats.csv", "resources/stats.csv"),
                "An intentionally blank path must not be replaced while saving other settings");
        assertEquals("resources/stats.dat",
                Configurator.pathToPersist(null, "stats.file", "resources/stats.dat"));
    }
    @Test
    public void savedPropertiesRoundTripWindowsPathsAndSpecialCharacters(@TempDir Path dir) throws Exception {
        Properties values = new Properties();
        values.setProperty("repositories.file", "C:\\users\\rober\\repos.txt");
        values.setProperty("stats.file", "C:\\course\\stats.dat");
        values.setProperty("stats.csv", "C:\\new\\table.csv");
        values.setProperty("teacher.user", "  José=profesor: #1!");
        values.setProperty("teacher.email", "first\nsecond\tvalue\r");
        Path file = dir.resolve("config.properties");
        Configurator.storeProperties(values, file);
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        }
        assertEquals(values, loaded);
        // A second save must not add an extra layer of escaping.
        Configurator.storeProperties(loaded, file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties twice = new Properties();
            twice.load(reader);
            assertEquals(values, twice);
        }
    }
}
