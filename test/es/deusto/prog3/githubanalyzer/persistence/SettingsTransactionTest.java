package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTransactionTest {
    private static final class InterruptedProcess extends Error { private static final long serialVersionUID = 1L; }
    private static final String OLD = "https://github.com/course/old;G1;3\n";
    private static final String NEW = "https://github.com/course/new;G2;4\n";

    @Test void startupRecoversBothFilesAfterInterruptionBetweenReplacements(@TempDir Path dir) throws Exception {
        checkRecovery(dir, false);
    }
    @Test void startupRecoversBothFilesIfInterruptedBeforeCommitDeletion(@TempDir Path dir) throws Exception {
        checkRecovery(dir, true);
    }
    private static void checkRecovery(Path dir, boolean bothReplaced) throws Exception {
        Configurator config = new Configurator(dir, () -> null);
        config.save("old-user", "old-token", "old-teacher", "old@example.org"); config.saveRepositories(OLD);
        Path file = dir.resolve("resources/config.properties");
        byte[] configBytes = Files.readAllBytes(file), repoBytes = Files.readAllBytes(Path.of(config.getRepositoriesFile()));
        assertThrows(InterruptedProcess.class, () -> SettingsTransaction.execute(dir, Path.of(config.getRepositoriesFile()), () -> {
            config.saveRepositories(NEW);
            if (bothReplaced) config.save("new-user", "new-token", "", "");
            throw new InterruptedProcess();
        }));
        assertTrue(Files.exists(SettingsTransaction.journal(dir)));
        Configurator restarted = new Configurator(dir, () -> null);
        assertArrayEquals(configBytes, Files.readAllBytes(file));
        assertArrayEquals(repoBytes, Files.readAllBytes(Path.of(restarted.getRepositoriesFile())));
        assertEquals("old-token", restarted.getGithubToken()); assertEquals("old-user", restarted.getGithubUser());
        assertFalse(Files.exists(SettingsTransaction.journal(dir)));
    }

    @Test void completedSaveSurvivesRestartAndRemovesJournal(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null); config.saveRepositories(OLD);
        config.saveSettings("new-user", "new-token", "", "", NEW);
        assertFalse(Files.exists(SettingsTransaction.journal(dir)));
        Configurator restarted = new Configurator(dir, () -> null);
        assertEquals("new-user", restarted.getGithubUser()); assertEquals(NEW, restarted.readRepositoriesText());
    }

    @Test void reportedFailureRestoresBothFilesWithoutRestart(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null); config.saveRepositories(OLD);
        assertThrows(IllegalStateException.class, () -> SettingsTransaction.execute(dir, Path.of(config.getRepositoriesFile()), () -> {
            config.saveRepositories(NEW); throw new IllegalStateException("Simulated write failure");
        }));
        assertEquals(OLD, config.readRepositoriesText());
        assertFalse(Files.exists(SettingsTransaction.journal(dir)));
    }

    @Test void interruptedSavePreservesUnicodeAndAbsoluteCustomRepositoryPaths(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null);
        Path custom = dir.resolve("curso-ñ/repos-á.txt"); Files.createDirectories(custom.getParent()); Files.writeString(custom, OLD);
        Path settings = dir.resolve("resources/config.properties");
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(settings)) { values.load(reader); }
        values.setProperty("repositories.file", custom.toString()); Configurator.storeProperties(values, settings); config.reload();
        assertThrows(InterruptedProcess.class, () -> SettingsTransaction.execute(dir, custom, () -> {
            config.saveRepositories(NEW); throw new InterruptedProcess();
        }));
        assertEquals(OLD, new Configurator(dir, () -> null).readRepositoriesText());
        assertEquals(custom, Path.of(config.getRepositoriesFile()));
    }

    @Test void failedRecoveryKeepsItsJournalAndCanBeRetried(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null); config.saveRepositories(OLD);
        Path repos = Path.of(config.getRepositoriesFile());
        assertThrows(InterruptedProcess.class, () -> SettingsTransaction.execute(dir, repos, () -> {
            config.saveRepositories(NEW); throw new InterruptedProcess();
        }));
        Files.delete(repos); Files.createDirectory(repos);
        assertThrows(IllegalStateException.class, () -> new Configurator(dir, () -> null));
        assertTrue(Files.exists(SettingsTransaction.journal(dir)));
        Files.delete(repos);
        assertEquals(OLD, new Configurator(dir, () -> null).readRepositoriesText());
        assertFalse(Files.exists(SettingsTransaction.journal(dir)));
    }

    @Test void journalContainingCredentialsIsOwnerOnlyOnPosix(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null); config.saveRepositories(OLD);
        SettingsTransaction.execute(dir, Path.of(config.getRepositoriesFile()), () -> {
            Path journal = SettingsTransaction.journal(dir);
            try {
                if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
                    assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(journal));
                }
            } catch (IOException ex) { throw new IllegalStateException(ex); }
        });
        assertFalse(Files.exists(SettingsTransaction.journal(dir)));
    }

    @Test void malformedJournalIsPreservedWithoutChangingSettings(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null); config.saveRepositories(OLD);
        Path file = dir.resolve("resources/config.properties"); byte[] original = Files.readAllBytes(file);
        Files.writeString(SettingsTransaction.journal(dir), "version=unknown\n");
        assertThrows(IllegalStateException.class, () -> new Configurator(dir, () -> null));
        assertArrayEquals(original, Files.readAllBytes(file)); assertEquals(OLD, config.readRepositoriesText());
        assertTrue(Files.exists(SettingsTransaction.journal(dir)));
    }
}
