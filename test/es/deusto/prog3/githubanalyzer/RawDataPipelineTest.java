package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.*;
import es.deusto.prog3.githubanalyzer.domain.*;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.*;
import es.deusto.prog3.githubanalyzer.persistence.RawDataStore;

class RawDataPipelineTest {
    private static final String URL = "https://github.com/course/project";
    private static final AuthorIdentity A = new AuthorIdentity("Name", "school@example.org", "login");
    private static final AuthorIdentity B = new AuthorIdentity("Alias", "home@example.org", "login");

    private static class Repository extends GHRepository {
        final Map<String, GHCommit> commits = new LinkedHashMap<>();
        final Map<String, List<String>> parents = new LinkedHashMap<>();
        final Map<String, String> heads = new LinkedHashMap<>();
        final Map<String, Integer> fileReads = new HashMap<>();
        final List<String> commitReads = new ArrayList<>();
        int sourceReads, comparisons, queries;
        boolean paginated;
        @Override public void setCompareUsePaginatedCommits(boolean enabled) { paginated = enabled; }
        String failSha;
        String blob = "blob-v1";
        String defaultBranch = "main";
        boolean privateRepository;
        String sourceText = "// IAG\nclass Same {}\n";
        void add(String sha, AuthorIdentity author, int added, String... ancestors) {
            parents.put(sha, List.of(ancestors));
            commits.put(sha, new GHCommit() {
                @Override public String getSHA1() { return sha; }
                @Override public Date getCommitDate() { return new Date(1000 + commits.size()); }
                @Override public List<GHCommit> getParents() { return Arrays.stream(ancestors).<GHCommit>map(id -> new GHCommit() { @Override public String getSHA1() { return id; } }).toList(); }
                @Override public GHUser getAuthor() { return null; }
                @Override public ShortInfo getCommitShortInfo() {
                    return new ShortInfo() { @Override public GitUser getAuthor() {
                        return new GitUser() { @Override public String getName() { return author.name(); } @Override public String getEmail() { return author.email(); } };
                    }};
                }
                @Override public PagedIterable<GHCommit.File> listFiles() throws IOException {
                    fileReads.merge(sha, 1, Integer::sum);
                    if (sha.equals(failSha)) throw new IOException("Simulated incomplete commit");
                    GHCommit.File file = new GHCommit.File() {
                        @Override public String getFileName() { return "src/Same.java"; }
                        @Override public int getLinesAdded() { return added; }
                        @Override public int getLinesDeleted() { return 0; }
                    };
                    return org.kohsuke.github.OfflinePages.of(new GHCommit.File[] {file});
                }
            });
        }
        Set<String> reachable(String head) {
            Set<String> result = new LinkedHashSet<>(); var todo = new ArrayDeque<String>(); todo.add(head);
            while (!todo.isEmpty()) { String sha = todo.remove(); if (result.add(sha)) todo.addAll(parents.getOrDefault(sha, List.of())); }
            return result;
        }
        @Override public String getFullName() { return "course/project"; }
        @Override public Date getCreatedAt() { return new Date(1); }
        @Override public Date getPushedAt() { return new Date(1000); }
        @Override public int getSize() { return heads.isEmpty() ? 0 : 1; }
        @Override public boolean isPrivate() { return privateRepository; }
        @Override public String getDefaultBranch() { return defaultBranch; }
        @Override public Map<String, GHBranch> getBranches() throws IOException {
            Map<String, GHBranch> result = new LinkedHashMap<>();
            for (var head : heads.entrySet()) result.put(head.getKey(), GitHub.getMappingObjectReader().forType(GHBranch.class)
                    .readValue("{\"name\":\"" + head.getKey() + "\",\"commit\":{\"sha\":\"" + head.getValue() + "\"}}"));
            return result;
        }
        @Override public GHCommit getCommit(String sha) { commitReads.add(sha); return commits.get(sha); }
        @Override public GHCommitQueryBuilder queryCommits() {
            queries++;
            Map<String, List<GHCommit>> histories = new HashMap<>();
            heads.values().forEach(head -> histories.put(head, reachable(head).stream().map(commits::get).toList()));
            return new org.kohsuke.github.OfflineCommitQuery(histories);
        }
        @Override public GHCompare getCompare(String base, String head) {
            assertTrue(paginated, "Explicitly enable paginated comparisons before counting commits");
            comparisons++;
            Set<String> delta = reachable(head); delta.removeAll(reachable(base));
            GHCompare.Commit[] values = delta.stream().map(sha -> new GHCompare.Commit() { @Override public String getSHA1() { return sha; } }).toArray(GHCompare.Commit[]::new);
            return new GHCompare() {
                @Override public PagedIterable<GHCompare.Commit> listCommits() { return org.kohsuke.github.OfflinePages.of(values); }
                @Override public GHCompare.Commit[] getCommits() { throw new AssertionError("Use paginated metadata, not a truncated comparison"); }
            };
        }
        @Override public List<GHContent> getDirectoryContent(String path, String ref) {
            assertEquals(heads.get(defaultBranch), ref, "The source snapshot must be pinned to the checked head SHA");
            return List.of(new GHContent() {
                @Override public boolean isDirectory() { return false; }
                @Override public String getName() { return "Same.java"; }
                @Override public String getPath() { return "src/Same.java"; }
                @Override public String getSha() { return blob; }
                @Override public InputStream read() { sourceReads++; return new ByteArrayInputStream(sourceText.getBytes(StandardCharsets.UTF_8)); }
            });
        }
    }
    private static GitHubDataLoader loader(Repository remote, RawDataStore store, AtomicInteger clients) {
        return new GitHubDataLoader(() -> List.of(URL + ";G01;4"), ignored -> new AuthorshipConfig(),
                () -> { clients.incrementAndGet(); return url -> remote; }, ignored -> { }, ignored -> { }, store::load, store::save, store::loadForUpdate);
    }
    private static Repository initial() {
        Repository remote = new Repository(); remote.add("a", A, 10); remote.heads.put("main", "a"); return remote;
    }
    @Test void unificationSplitAndTeamSizeChangesNeverReadApiOrRewriteOriginals(@TempDir Path dir) throws Exception {
        Repository remote = initial(); remote.add("b", B, 20, "a"); remote.heads.put("main", "b");
        RawDataStore store = new RawDataStore(dir); AtomicInteger clients = new AtomicInteger(); GitHubDataLoader loader = loader(remote, store, clients);
        loader.loadData(); byte[] original = Files.readAllBytes(store.pathFor(URL));
        assertEquals(2, loader.discoverAuthors(URL).size());
        AuthorshipConfig rules = new AuthorshipConfig(); rules.putPerson(new Person("person", "One person", Role.MEMBER, B.email(), ""));
        // GitHub login is absent in this fixture; author metadata remains untouched.
        for (var summary : loader.discoverAuthors(URL)) rules.assign(summary.identity().key(), "person");
        RepoStats merged = loader.recalculateRepository(URL, rules);
        assertEquals(B.email(), merged.getUserStats().get(0).getEmail());
        assertEquals(1, merged.getUserStats().size()); assertEquals(30, merged.getUserStats().get(0).getChurn()); assertEquals(1, merged.getUserStats().get(0).getJavaFiles());
        RepoStats split = loader.recalculateRepository(URL, new AuthorshipConfig());
        assertEquals(2, split.getUserStats().size()); assertEquals(2, split.getAuthorCount());
        assertEquals(1, clients.get()); assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        assertEquals(Map.of("a", 1, "b", 1), remote.fileReads);
        assertEquals(1, remote.sourceReads);
    }
    @Test void incrementalDownloadOnlyExpandsNewMergeAndSideBranchCommits(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData();
        remote.add("b", B, 20, "a"); remote.add("merge", A, 999, "a", "b"); remote.heads.put("main", "merge");
        loader.refreshRepository(URL);
        assertEquals(Set.of("merge", "b"), new HashSet<>(remote.commitReads));
        assertEquals(Map.of("a", 1, "b", 1, "merge", 1), remote.fileReads);
        assertEquals(1, remote.sourceReads, "Unchanged source blobs are not downloaded again");
        RepoStats stats = loader.recalculateRepository(URL, new AuthorshipConfig());
        assertEquals(3, stats.getCommits()); assertEquals(1, stats.getMergeCommits()); assertEquals(30, stats.getLinesChanged());
        loader.refreshRepository(URL);
        assertEquals(3, remote.fileReads.size()); assertEquals(2, remote.commitReads.size());
    }
    @Test void updateCheckCountsMissingShasAcrossBranchesWithoutReadingFilesOrWritingRawData(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); byte[] before = Files.readAllBytes(store.pathFor(URL));
        remote.add("b", B, 20, "a"); remote.heads.put("main", "b"); remote.heads.put("feature", "b");
        List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>(); loader.checkForUpdates((url, status) -> updates.add(status));
        assertEquals(1, updates.get(0).pendingCommits()); assertTrue(updates.get(0).headsChanged());
        assertEquals(Map.of("a", 1), remote.fileReads); assertTrue(remote.commitReads.isEmpty());
        assertArrayEquals(before, Files.readAllBytes(store.pathFor(URL)));
    }
    @Test void rewrittenAndRemovedBranchesRetainAllOriginalCommits(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); var first = store.load(URL).commits().get(0);
        remote.add("rewritten", B, 50); remote.heads.put("main", "rewritten"); loader.refreshRepository(URL);
        assertEquals(2, store.load(URL).commits().size()); assertTrue(store.load(URL).commits().contains(first));
        assertEquals(1, store.load(URL).activeCommits().size());
        remote.heads.clear(); loader.refreshRepository(URL);
        assertEquals(2, store.load(URL).commits().size()); assertTrue(store.load(URL).activeCommits().isEmpty());
    }
    @Test void failedIncrementalDownloadCannotAdvanceCheckpointOrReplaceRawData(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); byte[] before = Files.readAllBytes(store.pathFor(URL));
        remote.add("b", B, 20, "a"); remote.heads.put("main", "b"); remote.failSha = "b";
        assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL));
        assertArrayEquals(before, Files.readAllBytes(store.pathFor(URL)));
        assertEquals("a", store.load(URL).branchHeads().get("main"));
        remote.failSha = null; loader.refreshRepository(URL);
        assertEquals("b", store.load(URL).branchHeads().get("main")); assertEquals(1, remote.fileReads.get("a"));
    }
    @Test void localStartupDoesNotInitializeAClientAndMissingDownloadsStayPending(@TempDir Path dir) {
        RawDataStore store = new RawDataStore(dir);
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(),
                () -> { throw new AssertionError("Local analysis must not connect to GitHub"); }, ignored -> { }, ignored -> { }, store::load, store::save, store::loadForUpdate);
        assertTrue(loader.loadLocalData().isEmpty());
        assertThrows(IllegalStateException.class, () -> loader.discoverAuthors(URL));
        Repository remote = initial(); loader(remote, store, new AtomicInteger()).loadData();
        assertEquals(1, loader.loadLocalData().size()); assertEquals(1, loader.discoverAuthors(URL).size());
    }
    @Test void updateCheckUsesAllComparisonPagesBeyondTwoHundredFiftyCommits(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); String previous = "a";
        for (int i = 0; i < 501; i++) { String sha = "new-" + i; remote.add(sha, B, 1, previous); previous = sha; }
        remote.heads.put("main", previous);
        List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>(); loader.checkForUpdates((url, status) -> updates.add(status));
        assertEquals(501, updates.get(0).pendingCommits()); assertEquals(Map.of("a", 1), remote.fileReads);
        assertTrue(remote.commitReads.isEmpty());
    }
    @Test void failedBackgroundUpdateRetainsUsableLocalProjection(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); remote.add("bad", B, 20, "a"); remote.heads.put("main", "bad"); remote.failSha = "bad";
        List<RepoStats> stats = loader.loadData();
        assertEquals(0, loader.getLastConfirmedRepositoryCount()); assertEquals(1, stats.size());
        assertEquals(10, stats.get(0).getLinesChanged()); assertEquals("a", store.load(URL).branchHeads().get("main"));
    }
    @Test void aMinimalRepositoryWithReportedZeroSizeStillDownloadsItsCommits(@TempDir Path dir) {
        Repository remote = new Repository() { @Override public int getSize() { return 0; } };
        remote.add("tiny", A, 1); remote.heads.put("main", "tiny");
        RawDataStore store = new RawDataStore(dir);
        RepoStats stats = loader(remote, store, new AtomicInteger()).refreshRepository(URL);
        assertEquals(1, stats.getCommits()); assertEquals(1, stats.getLinesAdded());
        assertEquals("tiny", store.load(URL).branchHeads().get("main"));
    }
    @Test void defaultBranchChangeWithoutNewShasIsDetectedAndRefreshesTheFileSnapshot(@TempDir Path dir) {
        Repository remote = initial(); remote.add("b", B, 20, "a"); remote.heads.put("feature", "b");
        RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); int fileReads = remote.fileReads.values().stream().mapToInt(Integer::intValue).sum();
        remote.defaultBranch = "feature"; remote.blob = "feature-blob"; remote.sourceText = "// feature\nclass Same {\n int value;\n}\n";
        List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>(); loader.checkForUpdates((url, status) -> updates.add(status));
        assertEquals(0, updates.get(0).pendingCommits()); assertTrue(updates.get(0).headsChanged());
        assertEquals("main", store.load(URL).defaultBranch(), "The metadata check must not update the local checkpoint");
        RepoStats result = loader.refreshRepository(URL);
        assertEquals("feature", store.load(URL).defaultBranch()); assertEquals(4, result.getCodeLines());
        assertEquals(fileReads, remote.fileReads.values().stream().mapToInt(Integer::intValue).sum(), "Do not expand known commits for a metadata-only change");
        assertEquals(2, remote.sourceReads);
    }
    @Test void visibilityChangeWithoutNewShasIsDetectedAndSavedWithoutRedownloadingFiles(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); remote.privateRepository = true;
        List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>(); loader.checkForUpdates((url, status) -> updates.add(status));
        assertEquals(0, updates.get(0).pendingCommits()); assertTrue(updates.get(0).headsChanged());
        assertTrue(store.load(URL).publicRepository());
        assertFalse(loader.refreshRepository(URL).isPublic()); assertFalse(store.load(URL).publicRepository());
        assertEquals(1, remote.sourceReads); assertEquals(Map.of("a", 1), remote.fileReads); assertTrue(remote.commitReads.isEmpty());
    }
    @Test void unreadableOriginalsAreCopiedBeforeAnyReconstructionRequest(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); byte[] damaged = {1, 2, 3};
        Files.write(store.pathFor(URL), damaged); AtomicInteger requests = new AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(), () -> url -> {
            requests.incrementAndGet();
            List<Path> copies;
            try (var paths = Files.list(dir.resolve("recovery"))) { copies = paths.toList(); }
            assertEquals(1, copies.size()); assertArrayEquals(damaged, Files.readAllBytes(copies.get(0)));
            assertArrayEquals(damaged, Files.readAllBytes(store.pathFor(URL)), "Keep the damaged source in place until replacement is complete");
            return remote;
        }, ignored -> { }, ignored -> { }, store::load, store::save, store::loadForUpdate);
        assertEquals(1, loader.refreshRepository(URL).getCommits()); assertEquals(1, requests.get());
        assertEquals("a", store.load(URL).branchHeads().get("main"));
        try (var paths = Files.list(dir.resolve("recovery"))) { assertArrayEquals(damaged, Files.readAllBytes(paths.findFirst().orElseThrow())); }
    }
    @Test void failedReconstructionKeepsDamagedOriginalAndRecoveryCopiesAcrossRetry(@TempDir Path dir) throws Exception {
        Repository remote = initial(); remote.failSha = "a"; RawDataStore store = new RawDataStore(dir); byte[] damaged = {4, 5, 6};
        Files.write(store.pathFor(URL), damaged); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL));
        assertArrayEquals(damaged, Files.readAllBytes(store.pathFor(URL)));
        Path first;
        try (var paths = Files.list(dir.resolve("recovery"))) { List<Path> copies = paths.toList(); assertEquals(1, copies.size()); first = copies.get(0); }
        assertArrayEquals(damaged, Files.readAllBytes(first));
        remote.failSha = null; assertEquals(1, loader.refreshRepository(URL).getCommits());
        assertArrayEquals(damaged, Files.readAllBytes(first), "Retry must not overwrite the previous recovery copy");
        try (var paths = Files.list(dir.resolve("recovery"))) {
            List<Path> copies = paths.toList(); assertEquals(2, copies.size());
            for (Path copy : copies) assertArrayEquals(damaged, Files.readAllBytes(copy));
        }
    }
    @Test void failureToPreserveDamagedDataPreventsAllReconstructionRequests(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir); byte[] damaged = {7, 8, 9}; Files.write(store.pathFor(URL), damaged);
        Files.writeString(dir.resolve("recovery"), "blocked directory"); AtomicInteger requests = new AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(),
                () -> url -> { requests.incrementAndGet(); return initial(); }, ignored -> { }, ignored -> { }, store::load, store::save, store::loadForUpdate);
        assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL));
        assertEquals(0, requests.get()); assertArrayEquals(damaged, Files.readAllBytes(store.pathFor(URL)));
        assertEquals("blocked directory", Files.readString(dir.resolve("recovery")));
    }
}
