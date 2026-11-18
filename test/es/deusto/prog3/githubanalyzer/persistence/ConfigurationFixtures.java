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

import java.nio.file.Path;

/** Shares isolated configuration instances with GUI integration tests. */
public final class ConfigurationFixtures {
    private ConfigurationFixtures() { }

    public static void writeCsv(Path destination, java.util.List<es.deusto.prog3.githubanalyzer.domain.RepoStats> results) {
        DataManager.getInstance().storeCSV(results, destination);
    }

    public static void writeStatistics(Path destination, java.util.List<es.deusto.prog3.githubanalyzer.domain.RepoStats> results) {
        DataManager.getInstance().storeData(results, destination);
    }

    public static java.util.List<es.deusto.prog3.githubanalyzer.domain.RepoStats> readStatistics(Path source) {
        return DataManager.getInstance().loadData(source);
    }

    public static Configurator create(Path directory) {
        return new Configurator(directory);
    }
}
