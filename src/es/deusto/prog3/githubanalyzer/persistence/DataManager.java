package es.deusto.prog3.githubanalyzer.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
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
    		
    		this.storeCSV(data);
        } catch (Exception ex) {
        	System.err.format("* Error saving binary file: %s\n\n", ex.getMessage());
        }
	}
	
	public void storeCSV(List<RepoStats> data) {
		try (PrintWriter out = new PrintWriter(Configurator.getInstance().getStatsCSV())) {			
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
			out.println("GRUPO;URL;USER;EMAIL;CONTRIBUTION;ADDED;COMMITS;FIRST-COMMIT;LAST-COMMIT");			
			
			data.forEach(repoStats -> {
				repoStats.getUserStats().forEach(userStats -> {
					float contribution = ((float) userStats.getAdded()) / repoStats.getLinesAdded();
					
					out.format("%s;%s;%s;%s;%.2f;%d;%d;%s;%s\n", 
							"",
							repoStats.getUrl(),							
							userStats.getUsername(),
							userStats.getEmail(),
							contribution,
							userStats.getAdded(),							
							userStats.getCommits(),
							sdf.format(userStats.getFirstCommit()),
							sdf.format(userStats.getLastCommit()));
				});
			});			
			
			System.out.format("- %d RepoStats stored in '%s'\n\n", data.size(), Configurator.getInstance().getStatsCSV());
		} catch (Exception ex) {
			System.err.format("* Error saving CSV file: %s\n\n", ex.getMessage());
		}
	}
}