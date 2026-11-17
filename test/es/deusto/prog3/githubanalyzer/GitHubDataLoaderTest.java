// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */
package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/** Offline tests for complete analyses, original authors and explicit assignments. */
public class GitHubDataLoaderTest {

    @Test
    public void failedListReadAbortsRefreshWithoutReplacingTheCache(@TempDir Path dir) throws Exception {
        Path repositories = dir.resolve("repositories.txt");
        Files.writeString(repositories, "https://github.com/course/team;GROUP\n");
        GitHubDataLoader loader = new GitHubDataLoader(() -> {
            try {
                return Files.readAllLines(repositories, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalStateException("Repository list unavailable", ex);
            }
        });
        loader.reloadRepositories();
        assertEquals(1, loader.getConfiguredRepositoryCount());
        RepoStats cached = repo("https://github.com/course/team", "cached-team");
        cached.setCodeLines(42);
        List<RepoStats> cache = new ArrayList<>(List.of(cached));

        Files.delete(repositories);
        assertThrows(IllegalStateException.class, () -> loader.loadData(cache, true));
        assertEquals(1, loader.getConfiguredRepositoryCount(), "A failed read must retain the previous list");
        assertEquals(List.of(cached), cache);
        assertEquals(42, cached.getCodeLines());

        Files.writeString(repositories, "");
        loader.reloadRepositories();
        assertEquals(0, loader.getConfiguredRepositoryCount(), "A genuinely empty file is still accepted");
    }

    private static GHCommit.File changedFile(String path, int added, int deleted) {
        return new GHCommit.File() {
            @Override public String getFileName() { return path; }
            @Override public int getLinesAdded() { return added; }
            @Override public int getLinesDeleted() { return deleted; }
        };
    }

    private static GHCommit commitWithFiles(GHCommit.File... files) {
        String sha = java.util.UUID.randomUUID().toString();
        return new GHCommit() {
            @Override public String getSHA1() { return sha; }
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public ShortInfo getCommitShortInfo() { return null; }
            @Override public PagedIterable<GHCommit.File> listFiles() {
                return new PagedIterable<>() {
                    @Override public org.kohsuke.github.PagedIterator<GHCommit.File> _iterator(int pageSize) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public List<GHCommit.File> toList() { return List.of(files); }
                };
            }
        };
    }

    @Test
    public void explicitMergeUnionsJavaPathsAndPreservesEveryCommit() {
        GHCommit first = commitWithFiles(changedFile("Same.java", 10, 2));
        GHCommit second = commitWithFiles(changedFile("Same.java", 20, 3));
        AuthorIdentity a = new AuthorIdentity("Student", "school@example.org", "student");
        AuthorIdentity b = new AuthorIdentity("Alias", "home@example.org", "student");
        AuthorshipConfig rules = new AuthorshipConfig();
        rules.putPerson(new Person("student-id", "Student", Role.MEMBER));
        rules.assign(a.key(), "student-id");
        rules.assign(b.key(), "student-id");
        RepoStats repo = new RepoStats();
        fillStats(Map.of(a, List.of(first), b, List.of(second)), rules, repo, new StringBuilder());
        assertEquals(2, repo.getAuthorCount());
        assertEquals(1, repo.getUserStats().size());
        UserStats user = repo.getUserStats().get(0);
        assertEquals(2, user.getCommits());
        assertEquals(35, user.getChurn());
        assertEquals(1, user.getJavaFiles(), "Paths must be unioned, never summed across aliases");
        assertEquals("student-id", user.getPersonId());
    }

    @Test
    public void matchingLoginNameOrEmailLocalPartNeverMergesDifferentOriginalIdentities() {
        AuthorIdentity a = new AuthorIdentity("Student", "same@school.example", "same-login");
        AuthorIdentity b = new AuthorIdentity("Student", "same@home.example", "same-login");
        RepoStats repo = new RepoStats();
        fillStats(
                Map.of(a, List.of(commitWithFiles(changedFile("A.java", 1, 0))),
                       b, List.of(commitWithFiles(changedFile("B.java", 2, 0)))),
                new AuthorshipConfig(), repo, new StringBuilder());
        assertEquals(2, repo.getUserStats().size());
        assertEquals(2, repo.getAuthorCount());
    }

    @Test
    public void peopleWithIdenticalDisplayNamesAndEmailsRemainSeparate() {
        AuthorIdentity a = new AuthorIdentity("Same", "same@example.org", "first");
        AuthorIdentity b = new AuthorIdentity("Same", "same@example.org", "second");
        AuthorshipConfig rules = new AuthorshipConfig();
        rules.putPerson(new Person("one", "Same", Role.MEMBER));
        rules.putPerson(new Person("two", "Same", Role.TEACHER));
        rules.assign(a.key(), "one");
        rules.assign(b.key(), "two");
        RepoStats repo = new RepoStats();
        fillStats(
                Map.of(a, List.of(commitWithFiles(changedFile("A.java", 1, 0))), b, List.of(commitWithFiles(changedFile("notes.txt", 1, 0)))), rules, repo, new StringBuilder());
        assertEquals(2, repo.getUserStats().size());
        assertTrue(repo.getUserStats().stream().anyMatch(u -> u.getParticipationRole() == Role.TEACHER));
    }

    @Test
    public void javaFileCountsPreserveCaseWhileMatchingExtensionsIgnoringCase() {
        GHCommit first = commitWithFiles(
                changedFile("src/Foo.java", 10, 2), changedFile("src/foo.java", 20, 3),
                changedFile("SRC/Foo.java", 30, 4), changedFile("Other.JAVA", 40, 5),
                changedFile("notes.txt", 500, 100));
        GHCommit second = commitWithFiles(changedFile("src/Foo.java", 2, 1));
        UserStats stats = userStats(
                List.of(first, second), "student", "", new StringBuilder());
        assertEquals(4, stats.getJavaFiles(), "Case-distinct paths count separately; repeated paths count once");
        assertEquals(2, stats.getCommits());
        assertEquals(117, stats.getChurn(), "Non-Java edits must remain excluded");
    }

    private static es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit raw(GHCommit commit, AuthorIdentity identity) {
        try { return GitHubDataLoader.getInstance().downloadCommit(commit, identity); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static UserStats userStats(List<GHCommit> commits, String name, String email, StringBuilder ignored) {
        return StatisticsCalculator.userStats(commits.stream().map(c -> raw(c, new AuthorIdentity(name, email, ""))).toList(), name, email);
    }
    private static void fillStats(Map<AuthorIdentity, List<GHCommit>> values, AuthorshipConfig rules, RepoStats target, StringBuilder ignored) {
        var commits = new ArrayList<es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit>();
        values.forEach((identity, source) -> source.forEach(commit -> commits.add(raw(commit, identity))));
        Map<String, String> heads = new LinkedHashMap<>();
        commits.forEach(commit -> heads.put(commit.sha(), commit.sha()));
        var source = new es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot(1, AUDIT_URL, "repo", 1000, 0, 0, true, "main", heads, commits, List.of());
        RepoStats result = StatisticsCalculator.calculate(source, new es.deusto.prog3.githubanalyzer.domain.RepositoryConfig(AUDIT_URL, "", null), rules, identity -> Role.MEMBER);
        target.setUserStats(result.getUserStats()); target.setAuthorCount(result.getAuthorCount());
    }

    private static RepoStats repo(String url, String name) {
        RepoStats repo = new RepoStats();
        repo.setUrl(url);
        repo.setName(name);
        return repo;
    }

    @Test
    public void replacingOneFreshResultKeepsOtherResultsFromTheSameRun() {
        RepoStats oldA = repo("https://github.com/course/a", "a-old");
        RepoStats oldB = repo("https://github.com/course/b", "b-old");
        RepoStats refreshedA = repo("https://github.com/course/a", "a-new");

        List<RepoStats> updated = GitHubDataLoader.replaceResult(
                new ArrayList<>(List.of(oldA, oldB)), refreshedA);

        assertEquals(2, updated.size());
        assertSame(refreshedA, updated.get(0));
        assertSame(oldB, updated.get(1), "Other results from this run must not be lost");
    }

    private static final String AUDIT_URL = "https://github.com/test-audit/repo";

    private static class StubRepository extends GHRepository {
        List<GHContent> contents = List.of(javaContent(false));
        boolean failSnapshot;
        boolean failHistory;
        int snapshotReads;

        @Override public String getFullName() { return "test-audit/repo"; }
        @Override public Date getPushedAt() { return new Date(1000); }
        @Override public Date getCreatedAt() { return new Date(0); }
        @Override public int getSize() { return 10; }
        @Override public boolean isPrivate() { return false; }
        @Override public String getDefaultBranch() { return "master"; }
        @Override public Map<String, GHBranch> getBranches() throws IOException {
            if (!failHistory) return Map.of();
            GHBranch branch = GitHub.getMappingObjectReader().forType(GHBranch.class)
                    .readValue("{\"name\":\"master\",\"commit\":{\"sha\":\"test-sha\"}}");
            return Map.of("master", branch);
        }
        @Override public GHCommitQueryBuilder queryCommits() {
            throw new IllegalStateException("Simulated commit history API failure");
        }
        @Override public List<GHContent> getDirectoryContent(String path, String ref) throws IOException {
            snapshotReads++;
            if (failSnapshot) throw new IOException("Simulated snapshot API failure");
            return contents;
        }
    }

    private static GHContent javaContent(boolean failRead) {
        return new GHContent() {
            @Override public boolean isDirectory() { return false; }
            @Override public String getName() { return "Example.java"; }
            @Override public InputStream read() throws IOException {
                if (failRead) throw new IOException("Simulated file API failure");
                return new ByteArrayInputStream("// IAG\nclass Example {}\n".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static RepoStats cachedRepo(int snapshotVersion) {
        RepoStats cached = repo(AUDIT_URL, "repo");
        cached.setLastPushTime(1000);
        cached.setFileSnapshotVersion(snapshotVersion);
        cached.setCodeLines(42);
        cached.setGroup("OLD-GROUP");
        return cached;
    }

    @Test
    public void failedSnapshotRejectsReplacementAndAllowsRetryAtSamePush() {
        StubRepository repository = new StubRepository();
        RepoStats cached = cachedRepo(3);
        repository.failSnapshot = true;
        GitHubDataLoader loader = GitHubDataLoader.getInstance();
        assertNull(loader.analyzeRepository(AUDIT_URL, repository, List.of(cached), true));
        assertEquals(42, cached.getCodeLines());
        assertEquals("OLD-GROUP", cached.getGroup());
        repository.failSnapshot = false;
        RepoStats retried = loader.analyzeRepository(AUDIT_URL, repository, List.of(cached), true);
        assertEquals(2, retried.getCodeLines());
        assertEquals(1, retried.getExternalReferences());
        assertEquals(2, repository.snapshotReads);
    }

    @Test
    public void fileFailureAfterSuccessfulReadRejectsPartialSnapshot() {
        StubRepository repository = new StubRepository();
        repository.contents = List.of(javaContent(false), javaContent(true));
        RepoStats cached = cachedRepo(3);
        assertNull(GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cached), true));
        assertEquals(42, cached.getCodeLines());
    }

    @Test
    public void branchHistoryFailureRejectsOtherwiseValidSnapshot() {
        StubRepository repository = new StubRepository();
        repository.failHistory = true;
        assertNull(GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cachedRepo(3)), true));
        assertEquals(1, repository.snapshotReads);
    }

    @Test
    public void failedCommitExpansionCannotBecomeZeroContribution() {
        GHCommit commit = new GHCommit() {
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public ShortInfo getCommitShortInfo() { return null; }
            @Override public String getSHA1() { return "test-sha"; }
            @Override public PagedIterable<GHCommit.File> listFiles() throws IOException {
                throw new IOException("Simulated commit file API failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> userStats(List.of(commit), "student", "student@example.org", new StringBuilder()));
    }

    @Test
    public void emptyCommitFileListRemainsAValidZeroContribution() {
        GHCommit commit = new GHCommit() {
            @Override public String getSHA1() { return "empty-files"; }
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public ShortInfo getCommitShortInfo() { return null; }
            @Override public PagedIterable<GHCommit.File> listFiles() {
                return new PagedIterable<>() {
                    @Override public org.kohsuke.github.PagedIterator<GHCommit.File> _iterator(int pageSize) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public List<GHCommit.File> toList() { return List.of(); }
                };
            }
        };
        UserStats stats = userStats(
                List.of(commit), "student", "student@example.org", new StringBuilder());
        assertEquals(0, stats.getCommits());
        assertEquals(0, stats.getChurn());
    }

    @Test
    public void oldSnapshotVersionIsReanalyzedWithoutAnotherPush() {
        StubRepository repository = new StubRepository();
        RepoStats refreshed = GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cachedRepo(2)), false);
        assertEquals(1, repository.snapshotReads);
        assertEquals(4, refreshed.getFileSnapshotVersion());
        assertEquals(2, refreshed.getCodeLines());
    }

    @Test
    public void evenMatchingVersionAndPushAlwaysReadsGitHubAgain() {
        StubRepository repository = new StubRepository();
        RepoStats cached = cachedRepo(4);
        RepoStats result = GitHubDataLoader.getInstance().analyzeRepository(AUDIT_URL, repository, List.of(cached), false);
        org.junit.jupiter.api.Assertions.assertNotSame(cached, result);
        assertEquals(1, repository.snapshotReads);
        assertEquals(2, result.getCodeLines());
        assertEquals(42, cached.getCodeLines());
        assertTrue(result.isAnalysisComplete());
    }

    @Test
    public void aFailedFullRefreshNeverReturnsOrExportsCachedResults() {
        RepoStats old = cachedRepo(4);
        List<List<RepoStats>> writes = new ArrayList<>();
        List<List<RepoStats>> exports = new ArrayList<>();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(AUDIT_URL), url -> new AuthorshipConfig(),
                () -> url -> { throw new IOException("Offline test failure"); }, writes::add, exports::add);
        assertTrue(loader.loadData(List.of(old), false).isEmpty());
        assertEquals(0, loader.getLastConfirmedRepositoryCount());
        assertTrue(writes.isEmpty(), "A failed analysis must not persist any old result");
        assertTrue(exports.isEmpty(), "A failed refresh must keep the existing CSV");
        assertEquals(42, old.getCodeLines());
    }

    @Test
    public void successfulRefreshUsesNewTeamSettingsAndProgressivelyWritesOnlyFreshResults() {
        String failedUrl = "https://github.com/test-audit/failed";
        StubRepository repository = new StubRepository();
        List<List<RepoStats>> writes = new ArrayList<>();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(AUDIT_URL + ",G01,4", failedUrl), url -> new AuthorshipConfig(),
                () -> url -> { if (url.equals(failedUrl)) throw new IOException("Offline failure"); return repository; },
                writes::add, ignored -> { });
        List<RepoStats> results = loader.loadData(List.of(cachedRepo(4)), false);
        assertEquals(1, results.size());
        assertEquals(4, results.get(0).getTeamSize());
        assertEquals("G01", results.get(0).getGroup());
        assertEquals(2, results.get(0).getCodeLines());
        assertEquals(1, loader.getLastConfirmedRepositoryCount());
        assertEquals(2, writes.size(), "Progressive save followed by the final local projection");
        assertEquals(1, writes.get(0).size());
        assertEquals(1, writes.get(1).size());
    }

    @Test
    public void invalidRepositoryConfigurationDoesNotReplaceLastValidList() {
        java.util.concurrent.atomic.AtomicReference<List<String>> lines = new java.util.concurrent.atomic.AtomicReference<>(List.of(AUDIT_URL));
        GitHubDataLoader loader = new GitHubDataLoader(lines::get);
        loader.reloadRepositories();
        lines.set(List.of(AUDIT_URL + ",G01,0"));
        assertThrows(IllegalStateException.class, loader::reloadRepositories);
        assertEquals(1, loader.getConfiguredRepositoryCount());
        assertEquals(AUDIT_URL, loader.getRepositoryConfigs().get(0).url());
    }

    @Test
    public void invalidAuthorshipRejectsDerivedStatisticsIndependentlyOfOriginalDownload() {
        GitHubDataLoader loader = new GitHubDataLoader(List::of, url -> { throw new IllegalStateException("Invalid rules"); });
        StubRepository repository = new StubRepository();
        assertNull(loader.analyzeRepository(AUDIT_URL, repository, List.of(cachedRepo(4)), false));
        assertEquals(1, repository.snapshotReads, "Original download and attribution are separate stages");
    }

    @Test
    public void originalGitNameIsPreservedAlongsideGitHubLogin() throws Exception {
        org.kohsuke.github.GHUser user = GitHub.getMappingObjectReader().forType(org.kohsuke.github.GHUser.class)
                .readValue("{\"login\":\"account\"}");
        GHCommit.ShortInfo info = GitHub.getMappingObjectReader().forType(GHCommit.ShortInfo.class)
                .readValue("{\"author\":{\"name\":\"Original Git Name\",\"email\":\"original@example.org\"}}");
        GHCommit commit = new GHCommit() {
            @Override public org.kohsuke.github.GHUser getAuthor() { return user; }
            @Override public GHCommit.ShortInfo getCommitShortInfo() { return info; }
        };
        AuthorIdentity identity = GitHubDataLoader.getInstance().resolveAuthorRaw(commit);
        assertEquals("Original Git Name", identity.name());
        assertEquals("original@example.org", identity.email());
        assertEquals("account", identity.login());
    }

    @Test
    public void fullHistoryDeduplicatesBranchShasAndAppliesSavedAssignmentsAfterFreshDiscovery() throws Exception {
        GHCommit first = authoredCommit("sha-one", "Original", "first@example.org", 10);
        GHCommit second = authoredCommit("sha-two", "Alias", "second@example.org", 20);
        Map<String, List<GHCommit>> histories = Map.of("sha-one", List.of(first, second), "sha-two", List.of(first));
        GHBranch one = GitHub.getMappingObjectReader().forType(GHBranch.class).readValue("{\"name\":\"one\",\"commit\":{\"sha\":\"sha-one\"}}");
        GHBranch two = GitHub.getMappingObjectReader().forType(GHBranch.class).readValue("{\"name\":\"two\",\"commit\":{\"sha\":\"sha-two\"}}");
        StubRepository repository = new StubRepository() {
            @Override public Map<String, GHBranch> getBranches() { return Map.of("one", one, "two", two); }
            @Override public GHCommitQueryBuilder queryCommits() { return new org.kohsuke.github.OfflineCommitQuery(histories); }
        };
        AuthorshipConfig rules = new AuthorshipConfig();
        rules.putPerson(new Person("person", "One student", Role.MEMBER));
        rules.assign(new AuthorIdentity("Original", "first@example.org", "").key(), "person");
        rules.assign(new AuthorIdentity("Alias", "second@example.org", "").key(), "person");
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(AUDIT_URL), url -> rules,
                () -> url -> { reads.incrementAndGet(); return repository; }, ignored -> { }, ignored -> { });
        loader.loadData();
        List<String> progress = new ArrayList<>();
        var discovery = loader.discoverAuthors(AUDIT_URL, progress::add);
        assertTrue(progress.get(0).contains("local"));
        assertEquals(2, discovery.size());
        assertEquals(2, discovery.stream().mapToInt(a -> a.javaCommits()).sum());
        RepoStats result = loader.recalculateRepository(AUDIT_URL, rules);
        assertEquals(1, reads.get(), "Discovery and unification must reuse the original download without another API read");
        assertEquals(2, result.getCommits());
        assertEquals(2, result.getAuthorCount());
        assertEquals(1, result.getUserStats().size());
        assertEquals(2, result.getUserStats().get(0).getCommits());
        assertEquals(30, result.getUserStats().get(0).getChurn());
        assertEquals(1, result.getUserStats().get(0).getJavaFiles());
    }

    @Test
    public void cancelledDiscoveryDoesNotBeginAnotherApiRead() throws Exception {
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(AUDIT_URL), url -> new AuthorshipConfig(),
                () -> url -> { reads.incrementAndGet(); return new StubRepository(); }, ignored -> { }, ignored -> { });
        java.util.concurrent.atomic.AtomicReference<Throwable> failure = new java.util.concurrent.atomic.AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                assertThrows(java.util.concurrent.CancellationException.class, () -> loader.discoverAuthors(AUDIT_URL));
            } catch (Throwable ex) { failure.set(ex); }
        });
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertNull(failure.get());
        assertEquals(0, reads.get());
    }

    private static GHCommit authoredCommit(String sha, String name, String email, int added) {
        return new GHCommit() {
            @Override public String getSHA1() { return sha; }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public org.kohsuke.github.GHUser getAuthor() { return null; }
            @Override public ShortInfo getCommitShortInfo() {
                return new ShortInfo() {
                    @Override public org.kohsuke.github.GitUser getAuthor() {
                        return new org.kohsuke.github.GitUser() {
                            @Override public String getName() { return name; }
                            @Override public String getEmail() { return email; }
                        };
                    }
                };
            }
            @Override public PagedIterable<GHCommit.File> listFiles() throws IOException { return commitWithFiles(changedFile("Same.java", added, 0)).listFiles(); }
        };
    }

    // ---------------- countExternalMarkers: external/AI reference markers ----------------

    @Test
    public void countsStandaloneMarkers() {
        assertEquals(1, GitHubDataLoader.countExternalMarkers("// IAG"));
        assertEquals(1, GitHubDataLoader.countExternalMarkers("(IAG)"));
        assertEquals(1, GitHubDataLoader.countExternalMarkers("// FUENTE-EXTERNA: https://example.com"));
        assertEquals(2, GitHubDataLoader.countExternalMarkers("IAG and FUENTE-EXTERNA on one line"));
        assertEquals(2, GitHubDataLoader.countExternalMarkers("IAG ... IAG again"));
    }

    @Test
    public void ignoresMarkersEmbeddedInIdentifiers() {
        // These would all be false positives without word boundaries.
        assertEquals(0, GitHubDataLoader.countExternalMarkers("int DIAGNOSTIC = 0;"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("drawDIAGRAM();"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("String iagValue; // lowercase, not a marker"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("IAGENERATIVA"));
    }

    @Test
    public void countExternalMarkersHandlesNullAndEmpty() {
        assertEquals(0, GitHubDataLoader.countExternalMarkers(null));
        assertEquals(0, GitHubDataLoader.countExternalMarkers(""));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("nothing to see here"));
    }

    @Test void commitAtGithubFileLimitCannotBeAcceptedAsComplete() {
        GHCommit commit = new GHCommit() {
            @Override public String getSHA1() { return "oversized"; }
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public PagedIterable<GHCommit.File> listFiles() {
                return org.kohsuke.github.OfflinePages.of(new GHCommit.File[3000]);
            }
        };
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> GitHubDataLoader.getInstance().downloadCommit(commit,
                        new es.deusto.prog3.githubanalyzer.domain.AuthorIdentity("student", "student@example.org", "")));
        assertTrue(error.getCause().getMessage().contains("3,000-file limit"));
    }
}
