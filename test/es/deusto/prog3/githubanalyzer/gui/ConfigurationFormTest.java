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

package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import es.deusto.prog3.githubanalyzer.persistence.ConfigurationFixtures;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;

/** Exercises the same editor and save operation used by the Config dialog. */
public class ConfigurationFormTest {
    @Test void removingRepositoryInEditorPreservesLocalArchives(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        String removed = "https://github.com/course/removed";
        String retained = "https://github.com/course/retained";
        Files.writeString(Path.of(config.getRepositoriesFile()), removed + "\n" + retained + "\n");
        Path original = dir.resolve("resources/raw-data/saved.dat");
        Path authorship = dir.resolve("resources/authorship/saved.properties");
        Files.createDirectories(original.getParent()); Files.createDirectories(authorship.getParent());
        Files.writeString(original, "original-download"); Files.writeString(authorship, "saved-authorship");
        var operation = new java.util.concurrent.atomic.AtomicReference<Runnable>();
        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            form.reposArea.setText(retained + "\n");
            operation.set(form.prepareSave());
        });
        operation.get().run();
        assertEquals(List.of(retained), config.getRepositories());
        assertEquals(List.of(retained), ConfigurationFixtures.create(dir).getRepositories());
        assertEquals("original-download", Files.readString(original));
        assertEquals("saved-authorship", Files.readString(authorship));
    }

    @Test void backgroundSaveUsesCapturedValuesAndLocksEditors(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        java.util.concurrent.atomic.AtomicReference<Runnable> operation = new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            form.userField.setText("captured-user"); form.reposArea.setText("https://github.com/course/captured;G;4\n");
            operation.set(form.prepareSave());
            form.userField.setText("later-edit"); form.reposArea.setText("https://github.com/course/later\n");
            form.setSaving(true);
            assertFalse(form.userField.isEnabled()); assertFalse(form.reposArea.isEnabled());
            form.setSaving(false);
            assertTrue(form.userField.isEnabled()); assertTrue(form.reposArea.isEnabled());
        });
        java.util.concurrent.CompletableFuture.runAsync(operation.get()).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("captured-user", config.getGithubUser());
        assertEquals("https://github.com/course/captured;G;4\n", config.readRepositoriesText());
    }
    @Test
    public void missingListStillAllowsSavingOtherSettingsWithoutRecreatingIt(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        config.save("old-user", "old-token", "old-teacher", "old@example.org");
        Path repositories = Path.of(config.getRepositoriesFile());
        Files.delete(repositories);
        Path cache = Path.of(config.getStatsFile());
        byte[] previousCache = {1, 2, 3};
        Files.write(cache, previousCache);

        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            assertFalse(form.repositoriesAvailable);
            assertFalse(form.reposArea.isEditable());
            assertFalse(form.reposArea.isEnabled());
            assertTrue(form.userField.isEnabled());
            assertTrue(form.tokenField.isEnabled());
            form.userField.setText("new-user");
            form.tokenField.setText("new-token");
            form.teacherUserField.setText("new-teacher");
            form.teacherEmailField.setText("new@example.org");
            form.save();
        });

        Configurator restarted = ConfigurationFixtures.create(dir);
        assertEquals("new-user", restarted.getGithubUser());
        assertEquals("new-token", restarted.getGithubToken());
        assertEquals("new-teacher", restarted.getTeacherUser());
        assertEquals("new@example.org", restarted.getTeacherEmail());
        assertFalse(Files.exists(repositories));
        assertArrayEquals(previousCache, Files.readAllBytes(cache));
    }

    @Test
    public void unreadableListRemainsUnchangedWhenSavingOtherSettings(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
        Files.write(repositories, invalidUtf8);

        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            assertFalse(form.repositoriesAvailable);
            assertFalse(form.reposArea.isEditable());
            form.teacherUserField.setText("new-teacher");
            form.save();
        });

        assertArrayEquals(invalidUtf8, Files.readAllBytes(repositories));
        Configurator restarted = ConfigurationFixtures.create(dir);
        assertEquals("new-teacher", restarted.getTeacherUser());
    }

    @Test
    public void readableListCanStillBeEditedAndSaved(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        config.saveRepositories("https://github.com/course/old-team\n");

        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            assertTrue(form.repositoriesAvailable);
            assertTrue(form.reposArea.isEditable());
            assertEquals("https://github.com/course/old-team\n", form.reposArea.getText());
            form.reposArea.setText("https://github.com/course/new-team;GROUP\n");
            form.userField.setText("new-user");
            form.save();
        });

        Configurator restarted = ConfigurationFixtures.create(dir);
        assertEquals("new-user", restarted.getGithubUser());
        assertEquals(List.of("https://github.com/course/new-team;GROUP"), restarted.getRepositories());
    }

    @Test
    public void failedSettingsSaveDoesNotOverwriteTheRepositoryList(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        byte[] previousList = Files.readAllBytes(repositories);
        Path destination = dir.resolve("resources/config.properties");
        Files.delete(destination);
        Files.createDirectory(destination);

        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            form.reposArea.setText("https://github.com/course/new-team\n");
            assertThrows(IllegalStateException.class, form::save);
        });

        assertArrayEquals(previousList, Files.readAllBytes(repositories));
    }
    @Test
    public void invalidTeamSizeIsRejectedBeforeSavingAnySettings(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        byte[] previousSettings = Files.readAllBytes(dir.resolve("resources/config.properties"));
        byte[] previousList = Files.readAllBytes(Path.of(config.getRepositoriesFile()));
        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            form.userField.setText("must-not-save");
            form.reposArea.setText("https://github.com/course/team,G01,0");
            assertThrows(IllegalStateException.class, form::save);
        });
        assertArrayEquals(previousSettings, Files.readAllBytes(dir.resolve("resources/config.properties")));
        assertArrayEquals(previousList, Files.readAllBytes(Path.of(config.getRepositoriesFile())));
    }
    @Test
    public void semicolonGroupAndTeamSizeCanBeSavedAndReadByTheEditor(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        String line = "https://github.com/course/team;G01;4\n";
        SwingUtilities.invokeAndWait(() -> {
            ConfigurationForm form = new ConfigurationForm(config);
            form.reposArea.setText(line);
            form.save();
        });
        assertEquals(line, config.readRepositoriesText());
        var parsed = es.deusto.prog3.githubanalyzer.domain.RepositoryConfig.parseLines(config.getRepositories()).get(0);
        assertEquals("G01", parsed.group());
        assertEquals(4, parsed.teamSize());
    }
}
