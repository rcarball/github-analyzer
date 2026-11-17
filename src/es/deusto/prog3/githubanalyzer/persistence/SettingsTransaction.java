package es.deusto.prog3.githubanalyzer.persistence;

import java.io.IOException;
import java.io.StringReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.Properties;

/** Recover a two-file save after an interrupted process, using one private undo journal. */
final class SettingsTransaction {
    private SettingsTransaction() { }

    static Path journal(Path base) { return base.resolve("resources/.settings-pending.properties"); }

    static synchronized void recover(Path base) throws IOException {
        if (!Files.exists(journal(base))) return;
        try (var channel = lockChannel(base); var lock = channel.lock()) {
            restore(base);
        }
    }

    static synchronized void execute(Path base, Path repositories, Runnable save) throws IOException {
        try (var channel = lockChannel(base); var lock = channel.lock()) {
            restore(base);
            Path config = base.resolve("resources/config.properties").toAbsolutePath().normalize();
            repositories = repositories.toAbsolutePath().normalize();
            if (config.equals(repositories) || (Files.exists(config) && !Files.isRegularFile(config)) || !Files.isRegularFile(repositories))
                throw new IOException("Invalid configuration destination or unreadable repository list.");
            boolean configExists = Files.exists(config);
            byte[] oldConfig = configExists ? Files.readAllBytes(config) : new byte[0];
            byte[] oldRepos = Files.readAllBytes(repositories);
            if (configExists && !repositoryPath(base, oldConfig).equals(repositories))
                throw new IOException("Repository path changed externally. Reopen configuration before saving.");
            Properties undo = new Properties();
            undo.setProperty("version", "1");
            undo.setProperty("config.exists", Boolean.toString(configExists));
            undo.setProperty("repositories", repositories.toString());
            undo.setProperty("config.bytes", Base64.getEncoder().encodeToString(oldConfig));
            undo.setProperty("repositories.bytes", Base64.getEncoder().encodeToString(oldRepos));
            // Preparation is atomic and owner-only, including the backed-up token.
            Configurator.storeProperties(undo, journal(base));
            try {
                save.run();
                // One atomic deletion commits both replacements. If the process
                // stops before this point, startup restores both previous files.
                Files.delete(journal(base));
            } catch (IOException | RuntimeException ex) {
                try { restore(base); }
                catch (IOException rollback) { ex.addSuppressed(rollback); }
                throw ex;
            }
        }
    }

    private static FileChannel lockChannel(Path base) throws IOException {
        Files.createDirectories(base.resolve("resources"));
        return FileChannel.open(base.resolve("resources/.settings.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

    private static Path repositoryPath(Path base, byte[] config) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(new String(config, StandardCharsets.UTF_8)));
        Path path = Path.of(properties.getProperty("repositories.file", "resources/repositories.txt"));
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
    }

    private static void restore(Path base) throws IOException {
        if (!Files.exists(journal(base))) return;
        Properties undo = new Properties();
        try (var reader = Files.newBufferedReader(journal(base), StandardCharsets.UTF_8)) { undo.load(reader); }
        catch (IllegalArgumentException ex) { throw new IOException("Invalid recovery journal; recovery data has been kept.", ex); }
        byte[] config, repos;
        Path destination;
        boolean configExists;
        try {
            if (!"1".equals(undo.getProperty("version"))) throw new IllegalArgumentException("Unsupported save journal.");
            String existed = undo.getProperty("config.exists");
            if (!"true".equals(existed) && !"false".equals(existed)) throw new IllegalArgumentException("Missing previous config state.");
            configExists = Boolean.parseBoolean(existed);
            config = Base64.getDecoder().decode(undo.getProperty("config.bytes"));
            repos = Base64.getDecoder().decode(undo.getProperty("repositories.bytes"));
            destination = Path.of(undo.getProperty("repositories")).toAbsolutePath().normalize();
            if ((configExists && !repositoryPath(base, config).equals(destination))
                    || destination.equals(base.resolve("resources/config.properties").toAbsolutePath().normalize()))
                throw new IllegalArgumentException("Invalid repository path in save journal.");
        } catch (RuntimeException ex) {
            throw new IOException("Could not recover the interrupted save; the journal has been kept.", ex);
        }
        replace(destination, repos);
        Path configFile = base.resolve("resources/config.properties");
        if (configExists) replace(configFile, config);
        else {
            if (Files.isDirectory(configFile)) throw new IOException("Recovery configuration path is a directory.");
            Files.deleteIfExists(configFile);
        }
        Files.delete(journal(base));
    }

    private static void replace(Path destination, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), ".restore-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (Files.isDirectory(destination)) throw new IOException("Save destination is a directory.");
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
