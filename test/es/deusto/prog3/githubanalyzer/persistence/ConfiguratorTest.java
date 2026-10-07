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

import java.util.Properties;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.Test;

public class ConfiguratorTest {

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
