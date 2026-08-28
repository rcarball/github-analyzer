package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Configurator {
	// Base directory for resolving relative resource paths: the folder containing
	// the JAR when packaged (so `java -jar` works from any working directory), or
	// the current working directory when run from the IDE / class files.
	// Declared BEFORE `instance` so it is initialized before the constructor runs.
	private static final Path BASE_DIR = computeBaseDir();

	private static Configurator instance = new Configurator();

	private Properties properties;
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
			properties.load(new FileReader(resolvePath(PROPERTIES_FILE)));
			githubUser = properties.getProperty("github.user");
			githubToken = properties.getProperty("github.token");
			loadFromGithub = "YES".equalsIgnoreCase(properties.getProperty("update.from.github"));
			statsFile = resolvePath(properties.getProperty("stats.file"));
			repositoriesFile = resolvePath(properties.getProperty("repositories.file"));
			statsCSV = resolvePath(properties.getProperty("stats.csv"));
			teacherUser = properties.getProperty("teacher.user");
			teacherEmail = properties.getProperty("teacher.email");
		} catch (Exception ex) {
			System.err.format("* Error reading properties file: %s", ex.getMessage());
		}
	}

	/** Resolves a (possibly relative) path against {@link #BASE_DIR}; absolute paths are kept as-is. */
	private static String resolvePath(String path) {
		if (path == null || path.isEmpty()) return path;
		Path p = Paths.get(path);
		return p.isAbsolute() ? path : BASE_DIR.resolve(p).toString();
	}

	private static Path computeBaseDir() {
		try {
			java.net.URL location = Configurator.class.getProtectionDomain().getCodeSource().getLocation();
			if (location != null) {
				Path p = Paths.get(location.toURI());
				// Packaged as a JAR: use the folder that contains it. Otherwise fall
				// back to the working directory (IDE / loose class files).
				if (Files.isRegularFile(p) && p.toString().endsWith(".jar") && p.getParent() != null) {
					return p.getParent();
				}
			}
		} catch (Exception ignore) {
			// fall through to the working directory
		}
		return Paths.get("").toAbsolutePath();
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