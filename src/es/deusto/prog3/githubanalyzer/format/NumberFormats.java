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

package es.deusto.prog3.githubanalyzer.format;

import java.util.Locale;

/**
 * Spanish numeric presentation, independent of the machine's language settings.
 */
public final class NumberFormats {
	private static final Locale SPANISH = Locale.forLanguageTag("es-ES");

	private NumberFormats() {
	}

	/**
	 * Display a fraction as a percentage; rounding never changes the underlying
	 * metric.
	 */
	public static String percentage(double fraction) {
		double percent = fraction * 100;
		return Double.isFinite(percent) ? String.format(SPANISH, "%.2f %%", percent) : "—";
	}

	/** Unquoted numeric fraction for a semicolon-separated Spanish CSV. */
	public static String csvShare(double fraction) {
		return Double.isFinite(fraction) ? String.format(SPANISH, "%.6f", fraction) : "-";
	}
}
