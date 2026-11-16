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
    @Test
    public void missingListStillAllowsSavingOtherSettingsWithoutRecreatingIt(@TempDir Path dir) throws Exception {
        Configurator config = ConfigurationFixtures.create(dir);
        config.save("old-user", "old-token", true, "old-teacher", "old@example.org");
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
            assertTrue(form.onlineBox.isEnabled());
            form.userField.setText("new-user");
            form.tokenField.setText("new-token");
            form.teacherUserField.setText("new-teacher");
            form.teacherEmailField.setText("new@example.org");
            form.onlineBox.setSelected(false);
            form.save();
        });

        Configurator restarted = ConfigurationFixtures.create(dir);
        assertEquals("new-user", restarted.getGithubUser());
        assertEquals("new-token", restarted.getGithubToken());
        assertEquals("new-teacher", restarted.getTeacherUser());
        assertEquals("new@example.org", restarted.getTeacherEmail());
        assertFalse(restarted.isLoadFromGithub());
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
            form.onlineBox.setSelected(false);
            form.save();
        });

        assertArrayEquals(invalidUtf8, Files.readAllBytes(repositories));
        Configurator restarted = ConfigurationFixtures.create(dir);
        assertEquals("new-teacher", restarted.getTeacherUser());
        assertFalse(restarted.isLoadFromGithub());
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
}
