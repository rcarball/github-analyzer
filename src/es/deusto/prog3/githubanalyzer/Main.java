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
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class Main {
	
    public static void main(String[] args) {
    	// The Configurator creates resources/ + default config files on first run.
    	Configurator config = Configurator.getInstance();

    	// Read cache (may be empty).
    	List<RepoStats> statsMap = DataManager.getInstance().loadData();
    	System.out.format("- Loaded %d repositories from cache.\n", statsMap.size());

    	// Only hit GitHub when there is a usable configuration. Otherwise open the
    	// GUI anyway so the user can fill it in via the config dialog.
    	if (config.isConfigured() && config.isLoadFromGithub()) {
	    GitHubDataLoader loader = GitHubDataLoader.getInstance();
	    List<RepoStats> refreshedStats = loader.loadData(statsMap, false);
	    if (DataManager.shouldStoreRefresh(loader.getConfiguredRepositoryCount(), refreshedStats)) {
	        statsMap = refreshedStats;
	        System.out.format("- Cache now contains %d repositories.\n", statsMap.size());
	    } else {
	        System.err.println("* Refresh returned no repositories; keeping the existing cache.");
	    }
    	} else if (!config.isConfigured()) {
    		System.out.println("- Not configured yet: opening the app so you can set it up (Config).");
    	}

    	final List<RepoStats> list = statsMap;

    	SwingUtilities.invokeLater(() -> new MainWindow(list));
   }
}
