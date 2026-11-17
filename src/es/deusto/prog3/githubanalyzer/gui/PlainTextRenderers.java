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

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Display untrusted Git metadata literally, including text starting with
 * <html>.
 */
final class PlainTextRenderers {
	private PlainTextRenderers() {
	}

	static void install(JTable table) {
		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
		renderer.putClientProperty("html.disable", Boolean.TRUE);
		table.setDefaultRenderer(Object.class, renderer);
	}

	static <T> void install(JComboBox<T> choices) {
		DefaultListCellRenderer renderer = new DefaultListCellRenderer();
		renderer.putClientProperty("html.disable", Boolean.TRUE);
		choices.setRenderer(renderer);
	}
}
