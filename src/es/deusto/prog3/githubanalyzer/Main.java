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
			System.err.println("Revisa username y token de GitHub en el fichero 'resources/config.properties'");
			System.exit(1);
		}
		
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
    	
		if (list.isEmpty()) {
			System.err.println("No hay repositorios, revisa el fichero 'resources/repositories.txt'");
			System.exit(1);
		}
    	
    	SwingUtilities.invokeLater(() -> {
			new MainWindow(list);
		});    	
   }
}