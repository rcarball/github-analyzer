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

package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	private final Path baseDir;

	private Configurator() {
		this(BASE_DIR);
	}

	/** Independent configuration rooted in a controlled directory (used by tests). */
	Configurator(Path baseDir) {
		this.baseDir = baseDir;
		ensureResources();
		load();
	}

	/** Reads config.properties into memory. Package-visible via {@link #reload()}. */
	private void load() {
		try {
			properties = new Properties();
			try (FileReader reader = new FileReader(resolvePath(PROPERTIES_FILE), StandardCharsets.UTF_8)) {
				properties.load(reader);
			}
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
			Files.createDirectories(baseDir.resolve("resources"));
			Path cfg = Paths.get(resolvePath(PROPERTIES_FILE));
			boolean firstRun = !Files.exists(cfg);
			if (firstRun) {
				Files.writeString(cfg, DEFAULT_CONFIG, StandardCharsets.UTF_8);
				System.out.format("- Created default config at '%s'%n", cfg);
			}
			Path repos = Paths.get(resolvePath(REPOSITORIES_FILE));
			// A missing list in an existing installation is an error, not a new
			// empty configuration that may replace the user's cached statistics.
			if (firstRun && !Files.exists(repos)) {
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
		// The dialog does not edit resource paths, so preserve the values originally
		// read from disk instead of silently resetting custom locations to defaults.
		String repositoriesPath = pathToPersist(properties, "repositories.file", REPOSITORIES_FILE);
		String statsPath = pathToPersist(properties, "stats.file", STATS_FILE);
		String csvPath = pathToPersist(properties, "stats.csv", STATS_CSV);

        Properties saved = new Properties();
        saved.setProperty("github.user", nz(user));
        saved.setProperty("github.token", nz(token));
        saved.setProperty("update.from.github", online ? "yes" : "no");
        saved.setProperty("repositories.file", repositoriesPath);
        saved.setProperty("stats.file", statsPath);
        saved.setProperty("stats.csv", csvPath);
        saved.setProperty("teacher.user", nz(tUser));
        saved.setProperty("teacher.email", nz(tEmail));
        try {
            storeProperties(saved, Paths.get(resolvePath(PROPERTIES_FILE)));
            properties = saved;
			this.githubUser = user;
			this.githubToken = token;
			this.loadFromGithub = online;
			this.teacherUser = tUser;
			this.teacherEmail = tEmail;
		} catch (Exception ex) {
			throw new IllegalStateException("Could not save configuration.", ex);
		}
	}

    /** Use the Properties encoder to preserve backslashes, whitespace and control characters. */
    static void storeProperties(Properties values, Path destination) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            values.store(writer, "GitHub Analyzer configuration.\nIMPORTANT: never commit this file with a real token.");
        }
    }

	/** Returns the raw contents of repositories.txt (for editing in the config dialog). */
	public String readRepositoriesText() {
		try {
			return Files.readString(Paths.get(getRepositoriesFile()), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new IllegalStateException("Could not read the repository list.", ex);
		}
	}

	/** Overwrites repositories.txt with the given text. */
	public synchronized void saveRepositories(String text) {
		try {
			Files.writeString(Paths.get(getRepositoriesFile()), text == null ? "" : text, StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new IllegalStateException("Could not save the repository list.", ex);
		}
	}

	private static String nz(String s) {
		return (s == null) ? "" : s;
	}

	/**
	 * Returns the original, un-resolved value for a resource path so saving the
	 * GUI form preserves user choices such as a relative or absolute location.
	 * Package-visible to keep this preservation rule directly unit-testable.
	 */
	static String pathToPersist(Properties source, String key, String defaultPath) {
		if (source == null) return defaultPath;
		return source.getProperty(key, defaultPath);
	}

	/** Resolves a (possibly relative) path against the base directory; absolute paths are kept as-is. */
	private String resolvePath(String path) {
		if (path == null || path.isEmpty()) return path;
		Path p = Paths.get(path);
		return p.isAbsolute() ? path : baseDir.resolve(p).toString();
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
		try {
			return Files.readAllLines(Paths.get(getRepositoriesFile()), StandardCharsets.UTF_8);
		} catch (Exception ex) {
			throw new IllegalStateException("Could not read the repository list; existing data has been kept.", ex);
		}
	}
}
