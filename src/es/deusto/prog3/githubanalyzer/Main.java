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
    	
    	if (Configurator.getInstance().isLoadFromGithub()) {
    		//SE obtienen las estadísticas desde GitHub
	    	statsMap = GitHubDataLoader.getInstance().loadData();		    	
	    	//Se guardan las estadísticas en un fichero binario
	    	DataManager.getInstance().storeData(statsMap);		    	
    	} else {
    		//Se leen las estadísticas desde un fichero binario
    		statsMap = DataManager.getInstance().loadData();
    	}    	
    	
    	final List<RepoStats> list = statsMap;
    	
    	SwingUtilities.invokeLater(() -> {
			new MainWindow(list);
		});    	
   }
}