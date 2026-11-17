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

package es.deusto.prog3.githubanalyzer.gui;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.persistence.CsvExportException;

/**
 * Shared completion path for startup, refresh, configuration and authorship
 * exports.
 */
final class CsvExportFeedback {
	private CsvExportFeedback() {
	}

	static boolean handle(Throwable error, Consumer<List<RepoStats>> displayResults,
			BiConsumer<String, String> showError) {
		for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
			if (error instanceof CsvExportException csv) {
				displayResults.accept(csv.results());
				showError.accept("CSV export failed",
						"Calculated statistics remain available in the application.\n\n" + csv.getMessage()
								+ "\n\nThe previous CSV was not replaced. If none existed, no CSV was created.\n"
								+ "Close the CSV in Excel if it is open, check the destination folder and try again.");
				return true;
			}
		}
		return false;
	}
}
