package es.deusto.prog3.githubanalyzer.persistence;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;

/** Local configuration, separate from generated statistics and analyzed repositories. */
public final class AuthorshipStore {
    private final Path directory;
    public AuthorshipStore(Path directory) { this.directory = directory; }
    public Path pathFor(String repositoryUrl) { return directory.resolve(AuthorIdentity.digest(repositoryUrl) + ".properties"); }

    public AuthorshipConfig load(String repositoryUrl) {
        Path file = pathFor(repositoryUrl);
        AuthorshipConfig result = new AuthorshipConfig();
        if (!Files.exists(file)) return result;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            if (!"1".equals(properties.getProperty("version")) || !repositoryUrl.equals(properties.getProperty("repository")))
                throw new IOException("Invalid authorship configuration header.");
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("person.") && key.endsWith(".name")) {
                    String id = key.substring(7, key.length() - 5);
                    result.putPerson(new Person(id, properties.getProperty(key),
                            Role.valueOf(properties.getProperty("person." + id + ".role", "")),
                            properties.getProperty("person." + id + ".email", ""),
                            properties.getProperty("person." + id + ".login", "")));
                }
            }
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("identity.")) result.assign(key.substring(9), properties.getProperty(key));
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read authorship settings for " + repositoryUrl + ".", ex);
        }
    }

    public void save(String repositoryUrl, AuthorshipConfig config) {
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".authorship-", ".tmp");
            Properties properties = new Properties();
            properties.setProperty("version", "1");
            properties.setProperty("repository", repositoryUrl);
            config.people().forEach((id, person) -> {
                if (!config.assignments().containsValue(id)) return;
                properties.setProperty("person." + id + ".name", person.name());
                properties.setProperty("person." + id + ".role", person.role().name());
                properties.setProperty("person." + id + ".email", person.email());
                properties.setProperty("person." + id + ".login", person.login());
            });
            config.assignments().forEach((identity, person) -> properties.setProperty("identity." + identity, person));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(writer, "Local authorship assignments");
            }
            try {
                Files.move(temporary, pathFor(repositoryUrl), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, pathFor(repositoryUrl), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save authorship settings.", ex);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
