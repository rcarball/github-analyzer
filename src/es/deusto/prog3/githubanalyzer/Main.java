/**
 * This code was developed with AI assistance (ChatGPT) and reviewed by the author (see the unit tests for the validated parts).
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
	    	statsMap = GitHubDataLoader.getInstance().loadData(statsMap, false);
	    	DataManager.getInstance().storeData(statsMap);
	    	System.out.format("- Stored %d repositories in cache.\n", statsMap.size());
    	} else if (!config.isConfigured()) {
    		System.out.println("- Not configured yet: opening the app so you can set it up (Config).");
    	}

    	final List<RepoStats> list = statsMap;

    	SwingUtilities.invokeLater(() -> new MainWindow(list));
   }
}