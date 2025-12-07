package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configurator {
	private static Configurator instance = new Configurator();
	
	private static Properties properties;
	private static final String PROPERTIES_FILE = "resources/config.properties";
	
	private String githubUser = null;
	private String githubToken = null;
	private boolean loadFromGithub = true;
	private String statsFile = "";
	private String repositoriesFile = "";
	private String statsCSV = "";
	private String teacherUser = "";
	private String teacherEmail = "";
	
	private Configurator() {
		try {
			properties = new Properties();
			properties.load(new FileReader(PROPERTIES_FILE));
			githubUser = properties.getProperty("github.user");
			githubToken = properties.getProperty("github.token");
			loadFromGithub = properties.getProperty("update.from.github").equalsIgnoreCase("YES") ? true : false;
			statsFile = properties.getProperty("stats.file");
			repositoriesFile = properties.getProperty("repositories.file");
			statsCSV = properties.getProperty("stats.csv");
			teacherUser = properties.getProperty("teacher.user");
			teacherEmail = properties.getProperty("teacher.email");
		} catch (Exception ex) {
			System.err.format("* Error reading properties file: %s", ex.getMessage());
		}
	}
	
	public boolean isConfigured() {
		return githubUser != null && 
			   githubToken != null && 
			   !githubUser.isEmpty() && 
			   !githubToken.isEmpty();
	}
	
	public static Configurator getInstance() {
		return instance;
	}

	public String getGithubUser() {
		return githubUser;
	}

	public String getGithubToken() {
		return githubToken;
	}

	public boolean isLoadFromGithub() {
		return loadFromGithub;
	}

	public String getStatsFile() {
		return statsFile;
	}

	public String getRepositoriesFile() {
		return repositoriesFile;
	}
	
	public String getStatsCSV() {
        return statsCSV;
	}
	
	public String getTeacherUser() {
		return teacherUser;
	}

	public String getTeacherEmail() {
		return teacherEmail;
	}

	public List<String> getRepositories() {
		List<String> result = new ArrayList<>();
		
		try (BufferedReader in = new BufferedReader(new FileReader(getRepositoriesFile()))) {
			String line;
			
			while ((line = in.readLine()) != null) {
				result.add(line);
			}			
		} catch (Exception ex) {
			System.err.format("* Error reading repository file: %s", ex.getMessage());		
		}
		
		return result;
	}
}