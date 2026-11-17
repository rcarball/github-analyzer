package es.deusto.prog3.githubanalyzer.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryConfigTest {
    private static final String URL = "https://github.com/course/project";
    private RepositoryConfig parse(String suffix) { return RepositoryConfig.parseLines(List.of(URL + suffix)).get(0); }

    @Test void supportsOptionalFieldsWithCommaCompatibility() {
        assertEquals(new RepositoryConfig(URL, "", null), parse(""));
        assertEquals(new RepositoryConfig(URL, "G01", null), parse(",G01"));
        assertEquals(new RepositoryConfig(URL, "", 4), parse(",4"));
        assertEquals(new RepositoryConfig(URL, "G01", 4), parse(",G01,4"));
        assertEquals(new RepositoryConfig(URL, "", 4), parse(",,4"));
        assertEquals(new RepositoryConfig(URL, "12", null), parse(",12,"));
        assertEquals(new RepositoryConfig(URL, "12", 4), parse(",12,4"));
        assertEquals(new RepositoryConfig(URL, "", 12), parse(";12"));
        assertEquals(new RepositoryConfig(URL, "G01", null), parse(";G01"));
    }
    @Test void validatesTeamSizeWithoutSilentlyTreatingInvalidNumbersAsGroups() {
        for (String suffix : List.of(",0", ",-1", ",2.5", ",2147483648", ",G,0", ",G,-1", ",G,text", ",G,3,extra", ";G,4"))
            assertThrows(IllegalArgumentException.class, () -> parse(suffix), suffix);
    }
    @Test void preservesCommentsWhitespaceAndEmptyOptionalFields() {
        assertEquals(List.of(new RepositoryConfig(URL, "G", 3)), RepositoryConfig.parseLines(List.of("", "  # comment", "  " + URL + " , G , 3  ")));
        assertEquals(new RepositoryConfig(URL, "", null), parse(",,"));
        assertEquals(new RepositoryConfig(URL, "G", null), parse(",G,"));
    }
    @Test void duplicateUrlsCombineCompatibleSettingsAndRejectConflictsWithLineNumber() {
        assertEquals(List.of(new RepositoryConfig(URL, "G", 4)), RepositoryConfig.parseLines(List.of(URL, URL + ",G", URL + ",4")));
        var error = assertThrows(IllegalArgumentException.class, () -> RepositoryConfig.parseLines(List.of("# comment", URL + ",G,3", URL + ",G,4")));
        assertTrue(error.getMessage().contains("line 3"));
        assertThrows(IllegalArgumentException.class, () -> RepositoryConfig.parseLines(List.of(URL + ",G", URL + ",H")));
    }
    @Test void acceptsRepositoryUrlsAndRejectsPagesCredentialsAndNonGithubHosts() {
        assertEquals(URL, parse(".git/").url());
        for (String url : List.of("not a URL", "https://github.com/course", URL + "/issues", "https://other.example/course/repo", URL + "?token=secret", "https://user:password@github.com/course/repo"))
            assertThrows(IllegalArgumentException.class, () -> RepositoryConfig.parseLines(List.of(url)));
    }
    @Test void semicolonSupportsGroupAndOptionalSizeInEveryForm() {
        assertEquals(new RepositoryConfig(URL, "G01", 4), parse(";G01;4"));
        assertEquals(new RepositoryConfig(URL, "G01", null), parse(";G01"));
        assertEquals(new RepositoryConfig(URL, "", 4), parse(";4"));
        assertEquals(new RepositoryConfig(URL, "", 4), parse(";;4"));
        assertEquals(new RepositoryConfig(URL, "12", null), parse(";12;"));
        assertEquals(new RepositoryConfig(URL, "12", 4), parse(";12;4"));
        assertEquals(new RepositoryConfig(URL, "G01", null), parse(";G01;"));
        assertEquals(new RepositoryConfig(URL, "G01", 4), parse(" ; G01 ; 4 "));
        var error = assertThrows(IllegalArgumentException.class, () -> parse(";G01;invalid"));
        assertTrue(error.getMessage().contains("line 1"));
        assertThrows(IllegalArgumentException.class, () -> parse(";G01;4;extra"));
    }

    @Test void repositoryIdentityDeduplicatesSchemeCaseAndGitSuffixWithoutOrphaningExistingData() {
        assertEquals(List.of(new RepositoryConfig("HTTP://GITHUB.COM/COURSE/Project", "G", 4)), RepositoryConfig.parseLines(List.of(
                "HTTP://GITHUB.COM/COURSE/Project.GIT/;G", URL + ";;4")));
        for (String path : List.of("./project", "../project", "course/.", "course/..", "course/.git"))
            assertThrows(IllegalArgumentException.class, () -> RepositoryConfig.parseLines(List.of("https://github.com/" + path)));
        assertThrows(IllegalArgumentException.class, () -> RepositoryConfig.parseLines(List.of(URL + ";G", "http://github.com/COURSE/PROJECT;H")));
    }

}
