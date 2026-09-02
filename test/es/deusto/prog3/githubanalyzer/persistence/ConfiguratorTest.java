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
}
