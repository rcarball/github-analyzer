package es.deusto.prog3.githubanalyzer.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;

public class DataManager {

	private static DataManager instance = new DataManager();
	
	private DataManager() { }
	
	public static DataManager getInstance() {
		return instance;
	}
	
	@SuppressWarnings("unchecked")
	public List<RepoStats> loadData() {
		List<RepoStats> data = null;
					
		try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(Configurator.getInstance().getStatsFile()))) {			
			data = (List<RepoStats>) in.readObject();			
			
			data.forEach(repoStats -> {
				repoStats.updateFirstAndLastCommit();
			});
			
			System.out.format("- %d RepoStats loaded from '%s'\n\n", data.size(), Configurator.getInstance().getStatsFile());
        } catch (Exception ex) {
        	System.err.format("* Error reading binary file: %s\n\n", ex.getMessage());
        }
		
		return data;
	}
	
	public void storeData(List<RepoStats> data) {
    	try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(Configurator.getInstance().getStatsFile()))) {    		
    		out.writeObject(data);
    		System.out.format("- %d RepoStats stored in '%s'\n\n", data.size(), Configurator.getInstance().getStatsFile());
        } catch (Exception ex) {
        	System.err.format("* Error saving binary file: %s\n\n", ex.getMessage());
        }
	}
}