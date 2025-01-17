package es.deusto.prog3.githubanalyzer;

import java.util.List;

import javax.swing.SwingUtilities;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.gui.MainWindow;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class Main {
	
    public static void main(String[] args) {    	
    	List<RepoStats> statsMap = null;
    	
    	//Se confima que está configurado el username, el token y que existe al menos un repositorio
		if (!Configurator.getInstance().isConfigured()) {
			System.err.println("Check GitHub username and token in 'resources/config.properties'.");
			System.exit(1);
		}
		
		//Se leen las estadísticas desde un fichero binario
		statsMap = DataManager.getInstance().loadData();		
		
    	if (Configurator.getInstance().isLoadFromGithub()) {
    		//Se obtienen las estadísticas desde GitHub
	    	statsMap = GitHubDataLoader.getInstance().loadData(statsMap);		    	
	    	//Se guardan las estadísticas en un fichero binario
	    	DataManager.getInstance().storeData(statsMap);		    	
    	}	
    	
    	final List<RepoStats> list = statsMap;
    	
		if (list.isEmpty()) {
			System.err.println("No repositories, check 'resources/repositories.txt'.");
			System.exit(1);
		}
    	
    	SwingUtilities.invokeLater(() -> {
			new MainWindow(list);
		});    	
   }
}