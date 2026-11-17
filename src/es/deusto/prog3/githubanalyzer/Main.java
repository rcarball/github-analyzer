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
package es.deusto.prog3.githubanalyzer;

import java.util.List;

import javax.swing.SwingUtilities;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.gui.MainWindow;

public class Main {
	
    public static void main(String[] args) {
        List<RepoStats> statsMap = new java.util.ArrayList<>();
        try {
            GitHubDataLoader loader = GitHubDataLoader.getInstance();
            loader.reloadRepositories();
            statsMap = loader.loadLocalData();
        } catch (RuntimeException ex) {
            System.err.println("* Analysis pending: " + ex.getMessage());
        } catch (ExceptionInInitializerError ex) {
            String reason = ex.getCause() == null ? ex.toString() : ex.getCause().getMessage();
            SwingUtilities.invokeLater(() -> javax.swing.JOptionPane.showMessageDialog(null,
                    "Could not load local settings. Check file access and restart.\n" + reason,
                    "GitHub Analyzer", javax.swing.JOptionPane.ERROR_MESSAGE));
            return;
        }

    	final List<RepoStats> list = statsMap;

    	SwingUtilities.invokeLater(() -> new MainWindow(list));
   }
}
