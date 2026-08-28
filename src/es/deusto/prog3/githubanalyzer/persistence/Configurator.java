/**
 * This code was developed with AI assistance (ChatGPT and Claude) and reviewed by the author 
 * (see the unit tests for the validated parts).
 */

package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
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

	private static final String PROPERTIES_FILE   = "resources/config.properties";
	private static final String REPOSITORIES_FILE = "resources/repositories.txt";
	private static final String STATS_FILE        = "resources/stats.dat";
	private static final String STATS_CSV         = "resources/stats.csv";

	// Written on first run when the files don't exist yet.
	private static final String DEFAULT_CONFIG =
			"# GitHub Analyzer configuration.\n" +
			"# IMPORTANT: never commit this file with a real token.\n" +
			"github.user=\n" +
			"github.token=\n" +
			"update.from.github=yes\n" +
			"repositories.file=" + REPOSITORIES_FILE + "\n" +
			"stats.file=" + STATS_FILE + "\n" +
			"stats.csv=" + STATS_CSV + "\n" +
			"teacher.user=\n" +
			"teacher.email=\n";

	private static final String DEFAULT_REPOS =
			"# One repository URL per line. Optionally append ;GROUP-ID.\n" +
			"# Example: https://github.com/OWNER/REPO;GROUP-01\n";

	private Properties properties;

	private String githubUser = null;
	private String githubToken = null;
	private boolean loadFromGithub = true;
	private String statsFile = "";
	private String repositoriesFile = "";
	private String statsCSV = "";
	private String teacherUser = "";
	private String teacherEmail = "";

	private Configurator() {
		ensureResources();
		load();
	}

	/** Reads config.properties into memory. Package-visible via {@link #reload()}. */
	private void load() {
		try {
			properties = new Properties();
			properties.load(new FileReader(resolvePath(PROPERTIES_FILE)));
			githubUser = properties.getProperty("github.user");
			githubToken = properties.getProperty("github.token");
			loadFromGithub = "YES".equalsIgnoreCase(properties.getProperty("update.from.github"));
			statsFile = resolvePath(properties.getProperty("stats.file", STATS_FILE));
			repositoriesFile = resolvePath(properties.getProperty("repositories.file", REPOSITORIES_FILE));
			statsCSV = resolvePath(properties.getProperty("stats.csv", STATS_CSV));
			teacherUser = properties.getProperty("teacher.user");
			teacherEmail = properties.getProperty("teacher.email");
		} catch (Exception ex) {
			System.err.format("* Error reading properties file: %s%n", ex.getMessage());
		}
	}

	/** Re-reads config.properties from disk (used by the Refresh button and the config dialog). */
	public synchronized void reload() {
		load();
	}

	/** Creates the resources folder and default config/repositories files if they are missing. */
	private void ensureResources() {
		try {
			Files.createDirectories(BASE_DIR.resolve("resources"));
			Path cfg = Paths.get(resolvePath(PROPERTIES_FILE));
			if (!Files.exists(cfg)) {
				Files.writeString(cfg, DEFAULT_CONFIG, StandardCharsets.UTF_8);
				System.out.format("- Created default config at '%s'%n", cfg);
			}
			Path repos = Paths.get(resolvePath(REPOSITORIES_FILE));
			if (!Files.exists(repos)) {
				Files.writeString(repos, DEFAULT_REPOS, StandardCharsets.UTF_8);
				System.out.format("- Created default repositories file at '%s'%n", repos);
			}
		} catch (Exception ex) {
			System.err.format("* Error creating resources: %s%n", ex.getMessage());
		}
	}

	/** Persists the given config values to config.properties and updates memory. */
	public synchronized void save(String user, String token, boolean online,
	                              String tUser, String tEmail) {
		this.githubUser = user;
		this.githubToken = token;
		this.loadFromGithub = online;
		this.teacherUser = tUser;
		this.teacherEmail = tEmail;

		String content =
				"# GitHub Analyzer configuration.\n" +
				"# IMPORTANT: never commit this file with a real token.\n" +
				"github.user=" + nz(githubUser) + "\n" +
				"github.token=" + nz(githubToken) + "\n" +
				"update.from.github=" + (loadFromGithub ? "yes" : "no") + "\n" +
				"repositories.file=" + REPOSITORIES_FILE + "\n" +
				"stats.file=" + STATS_FILE + "\n" +
				"stats.csv=" + STATS_CSV + "\n" +
				"teacher.user=" + nz(teacherUser) + "\n" +
				"teacher.email=" + nz(teacherEmail) + "\n";
		try {
			Files.writeString(Paths.get(resolvePath(PROPERTIES_FILE)), content, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			System.err.format("* Error writing config: %s%n", ex.getMessage());
		}
	}

	/** Returns the raw contents of repositories.txt (for editing in the config dialog). */
	public String readRepositoriesText() {
		try {
			return Files.readString(Paths.get(getRepositoriesFile()), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			return "";
		}
	}

	/** Overwrites repositories.txt with the given text. */
	public synchronized void saveRepositories(String text) {
		try {
			Files.writeString(Paths.get(getRepositoriesFile()), text == null ? "" : text, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			System.err.format("* Error writing repositories file: %s%n", ex.getMessage());
		}
	}

	private static String nz(String s) {
		return (s == null) ? "" : s;
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
			System.err.format("* Error reading repository file: %s%n", ex.getMessage());
		}

		return result;
	}
}
