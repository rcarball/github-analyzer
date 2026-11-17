// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */
package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

import org.junit.jupiter.api.Test;

public class ConfiguratorTest {

    @Test
    public void configurationTemplateLoadsWithExactValuesAndPaths(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Files.copy(Path.of("resources/config.properties.example"), dir.resolve("resources/config.properties"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        config.reload();

        assertEquals("_USERNAME_", config.getGithubUser());
        assertEquals("_TOKEN_", config.getGithubToken());
        assertEquals(dir.resolve("resources/repositories.txt"), Path.of(config.getRepositoriesFile()));
        assertEquals(dir.resolve("resources/stats.dat"), Path.of(config.getStatsFile()));
        assertEquals(dir.resolve("resources/stats.csv"), Path.of(config.getStatsCSV()));
        assertEquals(dir.resolve("resources/authorship"), config.getAuthorshipDirectory());
        assertEquals("_TEACHER_USERNAME_", config.getTeacherUser());
        assertEquals("_TEACHER_EMAIL_", config.getTeacherEmail());
    }

    @Test
    public void readmeConfigurationExampleMatchesTheTemplate() throws Exception {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);
        var block = java.util.regex.Pattern.compile("```properties\\R(.*?)\\R```", java.util.regex.Pattern.DOTALL)
                .matcher(readme);
        assertTrue(block.find(), "README must include a copyable configuration example");
        Properties documented = new Properties();
        documented.load(new java.io.StringReader(block.group(1)));
        Properties template = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("resources/config.properties.example"), StandardCharsets.UTF_8)) {
            template.load(reader);
        }
        assertEquals(template, documented, "README and the template must describe the same usable settings");
    }

    @Test
    public void repositoryListDistinguishesUtf8ContentFromAnExplicitlyEmptyFile(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        String text = "https://github.com/course/team;Grupo-Á\n";
        config.saveRepositories(text);
        assertEquals(java.util.List.of(text.strip()), config.getRepositories());
        assertEquals(text, config.readRepositoriesText());

        config.saveRepositories("");
        assertTrue(config.getRepositories().isEmpty());
        assertEquals("", config.readRepositoriesText());
    }

    @Test
    public void missingRepositoryListIsNotTreatedAsEmptyEvenAfterRestart(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        Files.delete(repositories);
        Path cache = Path.of(config.getStatsFile());
        byte[] previousCache = {1, 2, 3};
        Files.write(cache, previousCache);

        Configurator restarted = new Configurator(dir);
        assertFalse(Files.exists(repositories), "Startup must not recreate a missing list as an empty one");
        assertThrows(IllegalStateException.class, restarted::getRepositories);
        assertThrows(IllegalStateException.class, restarted::readRepositoriesText);
        assertArrayEquals(previousCache, Files.readAllBytes(cache));
    }

    @Test
    public void unreadableOrInvalidRepositoryListIsReported(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path repositories = Path.of(config.getRepositoriesFile());
        Files.delete(repositories);
        Files.createDirectory(repositories);
        assertThrows(IllegalStateException.class, config::getRepositories);
        assertThrows(IllegalStateException.class, config::readRepositoriesText);

        Files.delete(repositories);
        byte[] invalidUtf8 = {(byte) 0xC3, 0x28};
        Files.write(repositories, invalidUtf8);
        assertThrows(IllegalStateException.class, config::getRepositories);
        assertThrows(IllegalStateException.class, config::readRepositoriesText);
        assertArrayEquals(invalidUtf8, Files.readAllBytes(repositories));
    }

    @Test
    public void failedConfigurationSaveKeepsPreviousMemoryAndCanBeRetried(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        config.save("previous-user", "previous-token", "previous-teacher", "previous@example.org");
        Path destination = dir.resolve("resources/config.properties");
        Files.delete(destination);
        Files.createDirectory(destination);

        assertThrows(IllegalStateException.class,
                () -> config.save("next-user", "next-token", "next-teacher", "next@example.org"));
        assertEquals("previous-user", config.getGithubUser());
        assertEquals("previous-token", config.getGithubToken());
        assertEquals("previous-teacher", config.getTeacherUser());
        assertEquals("previous@example.org", config.getTeacherEmail());

        Files.delete(destination);
        config.save("next-user", "next-token", "next-teacher", "next@example.org");
        Configurator restarted = new Configurator(dir);
        assertEquals("next-user", restarted.getGithubUser());
        assertEquals("next-token", restarted.getGithubToken());
        assertEquals("next-teacher", restarted.getTeacherUser());
        assertEquals("next@example.org", restarted.getTeacherEmail());
    }

    @Test
    public void repositorySaveFailureIsReportedAndCanBeRetried(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path destination = Path.of(config.getRepositoriesFile());
        Files.delete(destination);
        Files.createDirectory(destination);
        assertThrows(IllegalStateException.class, () -> config.saveRepositories("https://github.com/course/team\n"));

        Files.delete(destination);
        config.saveRepositories("https://github.com/course/team\n");
        assertEquals(java.util.List.of("https://github.com/course/team"), config.getRepositories());
    }

    @Test
    public void pathToPersistKeepsCustomRelativeAndAbsolutePaths() {
        Properties properties = new Properties();
        properties.setProperty("repositories.file", "course-data/repos.txt");
        properties.setProperty("stats.file", "/tmp/github-analyzer/stats.dat");

        assertEquals("course-data/repos.txt",
                Configurator.pathToPersist(properties, "repositories.file", "resources/repositories.txt"));
        assertEquals("/tmp/github-analyzer/stats.dat",
                Configurator.pathToPersist(properties, "stats.file", "resources/stats.dat"));
    }

    @Test
    public void pathToPersistUsesDefaultOnlyWhenThePropertyIsMissing() {
        Properties properties = new Properties();
        properties.setProperty("stats.csv", "");

        assertEquals("resources/repositories.txt",
                Configurator.pathToPersist(properties, "repositories.file", "resources/repositories.txt"));
        assertEquals("", Configurator.pathToPersist(properties, "stats.csv", "resources/stats.csv"),
                "An intentionally blank path must not be replaced while saving other settings");
        assertEquals("resources/stats.dat",
                Configurator.pathToPersist(null, "stats.file", "resources/stats.dat"));
    }
    @Test
    public void savedPropertiesRoundTripWindowsPathsAndSpecialCharacters(@TempDir Path dir) throws Exception {
        Properties values = new Properties();
        values.setProperty("repositories.file", "C:\\users\\rober\\repos.txt");
        values.setProperty("stats.file", "C:\\course\\stats.dat");
        values.setProperty("stats.csv", "C:\\new\\table.csv");
        values.setProperty("teacher.user", "  José=profesor: #1!");
        values.setProperty("teacher.email", "first\nsecond\tvalue\r");
        Path file = dir.resolve("config.properties");
        Configurator.storeProperties(values, file);
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        }
        assertEquals(values, loaded);
        // A second save must not add an extra layer of escaping.
        Configurator.storeProperties(loaded, file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Properties twice = new Properties();
            twice.load(reader);
            assertEquals(values, twice);
        }
    }
    @Test
    public void savingSettingsPreservesCustomAuthorshipDirectoryAndDoesNotCreateRules(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path settings = dir.resolve("resources/config.properties");
        String original = Files.readString(settings).replace("authorship.directory=resources/authorship", "authorship.directory=course-data/people");
        Files.writeString(settings, original);
        config.reload();
        assertEquals(dir.resolve("course-data/people"), config.getAuthorshipDirectory());
        config.save("user", "test-token", "teacher", "teacher@example.org");
        Configurator restarted = new Configurator(dir);
        assertEquals(dir.resolve("course-data/people"), restarted.getAuthorshipDirectory());
        assertFalse(Files.exists(restarted.getAuthorshipDirectory()), "Rules are created only when the author editor explicitly saves");
    }
    @Test
    public void blankAuthorshipDirectoryCannotWriteRulesIntoTheWorkingDirectory(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path settings = dir.resolve("resources/config.properties");
        Files.writeString(settings, Files.readString(settings).replace("authorship.directory=resources/authorship", "authorship.directory="));
        config.reload();
        assertThrows(IllegalStateException.class, config::getAuthorshipDirectory);
    }
    @Test
    public void rawDirectoryIsPreservedAndObsoleteUpdateFlagIsIgnoredOnSave(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir);
        Path settings = dir.resolve("resources/config.properties");
        String text = Files.readString(settings).replace("raw.directory=resources/raw-data", "raw.directory=course-data/originals");
        Files.writeString(settings, text + "update.from.github=no\n"); config.reload();
        assertEquals(dir.resolve("course-data/originals"), config.getRawDataDirectory());
        config.save("test-user", "test-token", "teacher", "teacher@example.org");
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(settings)) { values.load(reader); }
        assertFalse(values.containsKey("update.from.github"));
        assertEquals("course-data/originals", values.getProperty("raw.directory"));
        assertFalse(Files.exists(config.getRawDataDirectory()));
        Files.writeString(settings, Files.readString(settings).replace("raw.directory=course-data/originals", "raw.directory=")); config.reload();
        assertThrows(IllegalStateException.class, config::getRawDataDirectory);
    }

    @Test void environmentTokenOverridesDiskWithoutBeingPersisted(@TempDir Path dir) throws Exception {
        var token = new java.util.concurrent.atomic.AtomicReference<String>("environment-secret");
        Configurator config = new Configurator(dir, token::get);
        config.save("user", "disk-secret", "", "");
        assertEquals("environment-secret", config.getGithubToken());
        assertEquals("disk-secret", config.getStoredGithubToken());
        assertTrue(config.isConfigured());
        String persisted = Files.readString(dir.resolve("resources/config.properties"));
        assertFalse(persisted.contains("environment-secret"));
        token.set("  ");
        assertEquals("disk-secret", config.getGithubToken());
        config.save("user", "", "", "");
        assertFalse(config.isConfigured());
        token.set("environment-secret");
        assertTrue(config.isConfigured());
    }

    @Test void configIsPrivateFromCreationAndAfterSaving(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null);
        Path file = dir.resolve("resources/config.properties");
        if (!Files.getFileStore(file).supportsFileAttributeView("posix")) return;
        var expected = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
        assertEquals(expected, Files.getPosixFilePermissions(file));
        Files.setPosixFilePermissions(file, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
        config.save("user", "private-token", "", "");
        assertEquals(expected, Files.getPosixFilePermissions(file));
        try (var paths = Files.list(file.getParent())) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test void failedCombinedSaveRestoresRepositoryList(@TempDir Path dir) throws Exception {
        Configurator config = new Configurator(dir, () -> null);
        config.save("old-user", "old-token", "", "");
        config.saveRepositories("https://github.com/course/old\n");
        Path file = dir.resolve("resources/config.properties");
        Files.delete(file); Files.createDirectory(file);
        assertThrows(IllegalStateException.class, () -> config.saveSettings("new-user", "new-token", "", "", "https://github.com/course/new\n"));
        assertEquals("https://github.com/course/old\n", config.readRepositoriesText());
        assertEquals("old-user", config.getGithubUser());
        Files.delete(file);
        config.saveSettings("new-user", "new-token", "", "", "https://github.com/course/new\n");
        assertEquals("new-user", config.getGithubUser());
        assertEquals("https://github.com/course/new\n", config.readRepositoriesText());
    }

}
