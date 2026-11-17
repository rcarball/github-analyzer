package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.kohsuke.github.*;
import es.deusto.prog3.githubanalyzer.domain.*;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.*;
import es.deusto.prog3.githubanalyzer.persistence.RawDataStore;

@ResourceLock(Resources.SYSTEM_OUT)
@ResourceLock(Resources.SYSTEM_ERR)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class RawDataPipelineTest {
    private static final String URL = "https://github.com/course/project";
    private static final AuthorIdentity A = new AuthorIdentity("Name", "school@example.org", "login");
    private static final AuthorIdentity B = new AuthorIdentity("Alias", "home@example.org", "login");

    private static class Repository extends GHRepository {
        final Map<String, GHCommit> commits = new LinkedHashMap<>();
        final Map<String, List<String>> parents = new LinkedHashMap<>();
        final Map<String, String> heads = new LinkedHashMap<>();
        final Map<String, Integer> fileReads = new HashMap<>();
        final Map<String, Long> authorDates = new HashMap<>();
        final List<String> commitReads = new ArrayList<>();
        final List<String> historyHeads = new ArrayList<>();
        int sourceReads, comparisons, queries;
        int historyPageSize = Integer.MAX_VALUE;
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
                @Override public List<String> getParentSHA1s() { return List.of(ancestors); }
                @Override public List<GHCommit> getParents() { throw new AssertionError("Do not eagerly fetch parent metadata"); }
                @Override public GHUser getAuthor() { return null; }
                @Override public ShortInfo getCommitShortInfo() {
                    return new ShortInfo() { @Override public GitUser getAuthor() {
                        return new GitUser() {
                            @Override public String getName() { return author.name(); }
                            @Override public String getEmail() { return author.email(); }
                            @Override public Date getDate() { return authorDates.containsKey(sha) ? new Date(authorDates.get(sha)) : null; }
                        };
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
            return new org.kohsuke.github.OfflineCommitQuery(histories, historyHeads::add, historyPageSize);
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

    @Test void manyBranchAliasesReadOnlyOneCompleteHistory(@TempDir Path dir) {
        Repository remote = initial();
        remote.add("b", B, 20, "a"); remote.add("tip", A, 30, "b");
        remote.heads.put("main", "tip");
        for (int i = 0; i < 30; i++) remote.heads.put("alias-" + i, "tip");
        remote.heads.put("old-release", "a");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(List.of("tip"), remote.historyHeads, "One paginated history instead of one per branch");
        assertEquals(1, remote.queries);
        assertTrue(remote.commitReads.isEmpty(), "Known branch tips need no individual metadata request");
        assertEquals(Map.of("a", 1, "b", 1, "tip", 1), remote.fileReads);
        assertEquals(3, result.getCommits()); assertEquals(60, result.getLinesChanged());
        assertEquals(remote.heads, store.load(URL).branchHeads());
    }

    @Test void realCommitMetadataProvidesMergeParentsWithoutFetchingParentObjects() throws Exception {
        GHCommit commit = GitHub.getMappingObjectReader().forType(GHCommit.class).readValue(
                "{\"sha\":\"merge\",\"parents\":[{\"sha\":\"first\"},{\"sha\":\"second\"}],"
                + "\"files\":[],\"commit\":{\"committer\":{\"date\":\"2026-01-01T00:00:00Z\"}}}");
        // No owning repository or HTTP connector: eagerly resolving parents
        // would fail, while reading IDs from this actual library object works.
        var raw = GitHubDataLoader.getInstance().downloadCommit(commit, A);
        assertEquals(List.of("first", "second"), raw.parents());
        assertTrue(raw.isMerge());
    }

    @Test void downloadRetainsBothAuthorAndCommitterDatesFromRealMetadata() throws Exception {
        GHCommit commit = GitHub.getMappingObjectReader().forType(GHCommit.class).readValue(
                "{\"sha\":\"rebased\",\"parents\":[],\"files\":[],\"commit\":{"
                + "\"author\":{\"name\":\"Name\",\"date\":\"2024-01-01T00:00:00Z\"},"
                + "\"committer\":{\"date\":\"2026-01-01T00:00:00Z\"}}}");
        var raw = GitHubDataLoader.getInstance().downloadCommit(commit, A);
        assertEquals(java.time.Instant.parse("2024-01-01T00:00:00Z").toEpochMilli(), raw.activityTimestamp());
        assertEquals(java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(), raw.timestamp());
        assertEquals(Long.valueOf(raw.activityTimestamp()), raw.authorTimestamp());
    }

    private static void makeLegacyDates(RawDataStore store) {
        var source = store.load(URL);
        store.save(new RepositorySnapshot(source.version(), source.url(), source.name(), source.capturedAt(), source.createdAt(),
                source.lastPushTime(), source.publicRepository(), source.defaultBranch(), source.branchHeads(),
                source.commits().stream().map(c -> new RepositorySnapshot.Commit(c.sha(), c.author(), c.timestamp(), c.parents(), c.files())).toList(), source.sourceFiles()));
    }

    @Test void explicitRefreshCompletesLegacyAuthorDatesOnceWithoutReadingFiles(@TempDir Path dir) {
        Repository remote = initial(); remote.authorDates.put("a", 500L);
        RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.refreshRepository(URL); makeLegacyDates(store); remote.commitReads.clear(); remote.fileReads.clear();
        int sourceReads = remote.sourceReads;
        RepoStats updated = loader.refreshRepository(URL);
        assertEquals(500, updated.getUserStats().get(0).getFirstCommit());
        assertEquals(List.of("a"), remote.commitReads); assertTrue(remote.fileReads.isEmpty());
        assertEquals(sourceReads, remote.sourceReads);
        assertEquals(Long.valueOf(500), store.load(URL).commits().get(0).authorTimestamp());
        loader.refreshRepository(URL);
        assertEquals(List.of("a"), remote.commitReads, "The completed date must not be requested twice");
    }

    @Test void failedDateUpgradeKeepsTheEntireLegacyDownload(@TempDir Path dir) throws Exception {
        Repository remote = new Repository() {
            @Override public GHCommit getCommit(String sha) { throw new IllegalStateException("Simulated metadata failure"); }
        };
        remote.add("a", A, 10); remote.heads.put("main", "a");
        RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.refreshRepository(URL); makeLegacyDates(store);
        byte[] previous = Files.readAllBytes(store.pathFor(URL));
        assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL));
        assertArrayEquals(previous, Files.readAllBytes(store.pathFor(URL)));
        assertNull(store.load(URL).commits().get(0).authorTimestamp());
    }

    @Test void initialHistoryConsumesAllPagesBeforeReusingAncestors(@TempDir Path dir) {
        Repository remote = initial(); remote.historyPageSize = 2;
        String parent = "a";
        for (int i = 1; i <= 4; i++) {
            String sha = "commit-" + i; remote.add(sha, A, 1, parent); parent = sha;
        }
        remote.heads.put("main", parent); remote.heads.put("old-feature", "a");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(5, result.getCommits());
        assertEquals(5, remote.fileReads.size());
        assertTrue(remote.commitReads.isEmpty(), "Later pages must not be fetched individually through parent traversal");
        assertEquals(List.of(parent), remote.historyHeads);
        assertEquals(5, store.load(URL).activeCommits().size());
    }

    @Test void mergeOnlySideHistoryIsFollowedEvenWhenFirstParentIsAlreadyKnown(@TempDir Path dir) {
        Repository remote = initial(); remote.add("main-tip", A, 20, "a");
        remote.add("hidden-base", B, 30, "a"); remote.add("hidden-tip", B, 40, "hidden-base");
        remote.add("merge", A, 999, "main-tip", "hidden-tip");
        remote.heads.put("main", "main-tip"); remote.heads.put("merged-feature", "merge");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(Set.of("merge", "hidden-tip", "hidden-base"), new HashSet<>(remote.commitReads));
        assertEquals(3, remote.commitReads.size());
        assertEquals(5, result.getCommits()); assertEquals(1, result.getMergeCommits());
        assertEquals(100, result.getLinesChanged());
        assertEquals(5, store.load(URL).activeCommits().size());
        assertEquals(List.of("main-tip"), remote.historyHeads);
    }

    @Test void divergentAndMergedBranchesFetchOnlyCommitsOutsideDefaultHistory(@TempDir Path dir) {
        Repository remote = initial();
        remote.add("b", A, 20, "a"); remote.add("main-tip", A, 30, "b");
        remote.add("shared-feature", B, 40, "b"); remote.add("feature-tip", B, 50, "shared-feature");
        // The first parent is already known; the second must still be explored.
        remote.add("merge", A, 999, "b", "shared-feature"); remote.add("orphan", B, 60);
        remote.heads.put("main", "main-tip"); remote.heads.put("aaa-merge", "merge");
        remote.heads.put("feature", "feature-tip"); remote.heads.put("shared-alias", "shared-feature");
        remote.heads.put("unrelated", "orphan");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(List.of("main-tip"), remote.historyHeads, "Start with the default branch, not alphabetical order");
        assertEquals(Set.of("shared-feature", "feature-tip", "merge", "orphan"), new HashSet<>(remote.commitReads));
        assertEquals(4, remote.commitReads.size(), "Shared parents must be fetched once across branches");
        assertEquals(7, remote.fileReads.size());
        assertTrue(remote.fileReads.values().stream().allMatch(count -> count == 1));
        assertEquals(7, result.getCommits()); assertEquals(1, result.getMergeCommits());
        assertEquals(210, result.getLinesChanged(), "Merge changes must not inflate Java churn");
        assertEquals(7, store.load(URL).activeCommits().size());
        assertEquals(remote.heads, store.load(URL).branchHeads());
    }

    @Test void unrelatedBranchesShareTheirOwnAncestorsWithoutListingMoreHistories(@TempDir Path dir) {
        Repository remote = initial();
        remote.add("other-root", B, 20); remote.add("left", B, 30, "other-root");
        remote.add("right", A, 40, "other-root");
        remote.heads.put("left", "left"); remote.heads.put("right", "right");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(List.of("a"), remote.historyHeads);
        assertEquals(Set.of("other-root", "left", "right"), new HashSet<>(remote.commitReads));
        assertEquals(3, remote.commitReads.size());
        assertEquals(4, result.getCommits()); assertEquals(100, result.getLinesChanged());
        assertEquals(4, store.load(URL).activeCommits().size());
    }

    @Test void missingParentsInAnInitialHistoryAreResolvedBeforeSaving(@TempDir Path dir) {
        Repository remote = new Repository() {
            @Override public GHCommitQueryBuilder queryCommits() {
                queries++;
                return new OfflineCommitQuery(Map.of("tip", List.of(commits.get("tip"))), historyHeads::add);
            }
        };
        remote.add("root", A, 10); remote.add("tip", B, 20, "root"); remote.heads.put("main", "tip");
        RawDataStore store = new RawDataStore(dir);
        RepoStats result = loader(remote, store, new AtomicInteger()).refreshRepository(URL);

        assertEquals(List.of("root"), remote.commitReads);
        assertEquals(Map.of("root", 1, "tip", 1), remote.fileReads);
        assertEquals(2, result.getCommits()); assertEquals(30, result.getLinesChanged());
        assertEquals(2, store.load(URL).activeCommits().size());
    }

    @Test void failureOnAnAdditionalBranchNeverSavesAPartialInitialDownload(@TempDir Path dir) {
        Repository remote = initial(); remote.add("failed-feature", B, 20, "a");
        remote.heads.put("feature", "failed-feature"); remote.failSha = "failed-feature";
        RawDataStore store = new RawDataStore(dir);
        GitHubDataLoader loader = loader(remote, store, new AtomicInteger());

        ConsoleOutput console = captureConsole(() -> assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL)));
        assertTrue(console.err().contains("Could not download commit failed-feature"));
        assertEquals(List.of("a"), remote.historyHeads);
        assertEquals(Map.of("a", 1, "failed-feature", 1), remote.fileReads);
        assertFalse(Files.exists(store.pathFor(URL)));

        remote.failSha = null;
        assertEquals(2, loader.refreshRepository(URL).getCommits());
        assertEquals(2, store.load(URL).activeCommits().size());
    }

    @Test void failedMultiBranchIncrementKeepsTheEntirePreviousCheckpoint(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir);
        GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.refreshRepository(URL); byte[] original = Files.readAllBytes(store.pathFor(URL));
        remote.add("good", B, 20, "a"); remote.add("bad", A, 30, "a");
        remote.heads.put("aa-good", "good"); remote.heads.put("zz-bad", "bad"); remote.failSha = "bad";

        assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL));
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        assertEquals(Map.of("main", "a"), store.load(URL).branchHeads());
        assertEquals(1, remote.queries, "Incremental work must not list any branch history again");
        assertEquals(1, remote.fileReads.get("a"));
    }

    @Test void cancellingAdditionalBranchTraversalStopsBeforeAnotherMetadataRequest(@TempDir Path dir) throws Exception {
        Repository remote = initial(); remote.add("feature-tip", B, 20, "a"); remote.heads.put("feature", "feature-tip");
        List<String> messages = new ArrayList<>();
        try {
            assertThrows(CancellationException.class, () -> GitHubDataLoader.getInstance().downloadSnapshot(URL, remote, message -> {
                messages.add(message);
                if (message.contains("Downloading new commit #1")) Thread.currentThread().interrupt();
            }));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(messages.stream().anyMatch(message -> message.contains("Downloading new commit #1")));
            assertTrue(remote.commitReads.isEmpty());
            assertEquals(Map.of("a", 1), remote.fileReads);
            assertEquals(List.of("a"), remote.historyHeads);
        } finally { Thread.interrupted(); }
    }
    private record ConsoleOutput(String out, String err) { }
    private static ConsoleOutput captureConsole(Runnable action) { return captureConsole(false, action); }
    private static ConsoleOutput captureConsole(boolean debug, Runnable action) {
        String oldDebug = System.getProperty("githubanalyzer.debug");
        ByteArrayOutputStream output = new ByteArrayOutputStream(), errors = new ByteArrayOutputStream();
        PrintStream oldOut = System.out, oldErr = System.err;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setProperty("githubanalyzer.debug", Boolean.toString(debug));
            System.setOut(out); System.setErr(err);
            action.run();
        } finally {
            System.setOut(oldOut); System.setErr(oldErr);
            if (oldDebug == null) System.clearProperty("githubanalyzer.debug");
            else System.setProperty("githubanalyzer.debug", oldDebug);
        }
        return new ConsoleOutput(output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }
    @Test void consoleProgressDistinguishesFullIncrementalUnchangedAndLocalWork(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); AtomicInteger clients = new AtomicInteger();
        GitHubDataLoader loader = loader(remote, store, clients);
        ConsoleOutput first = captureConsole(loader::loadData);
        assertEquals(4, first.out().lines().count(), first.out());
        assertTrue(first.out().contains("Starting GitHub synchronization"));
        assertTrue(first.out().contains("[download course/project] Starting repository"));
        assertTrue(first.out().contains("Repository complete (1/1)"));
        assertTrue(first.out().contains("Synchronization finished"));
        assertTrue(first.out().lines().allMatch(line -> line.matches("\\[\\d{2}:\\d{2}:\\d{2}\\] \\[[^\\]]+\\] .*")));
        assertTrue(first.err().isEmpty());

        remote.add("b", B, 20, "a"); remote.heads.put("main", "b");
        ConsoleOutput incremental = captureConsole(() -> loader.refreshRepository(URL));
        assertEquals(2, incremental.out().lines().count(), incremental.out());
        assertTrue(incremental.out().contains("Repository complete"));
        assertEquals(Map.of("a", 1, "b", 1), remote.fileReads); assertEquals(List.of("b"), remote.commitReads);

        byte[] original = Files.readAllBytes(store.pathFor(URL));
        ConsoleOutput unchanged = captureConsole(() -> loader.refreshRepository(URL));
        assertEquals(2, unchanged.out().lines().count(), unchanged.out());
        assertTrue(unchanged.out().contains("Repository complete"));
        assertFalse(unchanged.out().contains("Original download saved"));
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        assertEquals(Map.of("a", 1, "b", 1), remote.fileReads);

        int before = clients.get();
        ConsoleOutput local = captureConsole(() -> loader.recalculateRepository(URL, new AuthorshipConfig()));
        assertTrue(local.out().contains("[local course/project]")); assertTrue(local.out().contains("no GitHub requests"));
        assertTrue(local.out().contains("Authorship recalculated")); assertEquals(before, clients.get());
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        assertFalse((first.out() + incremental.out() + local.out()).contains(A.email()));
    }
    @Test void detailedDiagnosticsAreOptIn(@TempDir Path dir) {
        Repository remote = initial();
        GitHubDataLoader loader = loader(remote, new RawDataStore(dir), new AtomicInteger());
        ConsoleOutput console = captureConsole(true, () -> loader.refreshRepository(URL));
        assertTrue(console.out().contains("Full history ready: 1 unique commits"));
        assertTrue(console.out().contains("Original download saved"));
        assertTrue(console.out().contains("Repository complete"));
        assertTrue(console.err().isEmpty());
    }
    @Test void repositoryCompletionFollowsPersistenceAndSaveFailuresNameTheRepository(@TempDir Path dir) {
        for (boolean failSave : new boolean[] {false, true}) {
            Repository remote = initial(); RawDataStore store = new RawDataStore(dir.resolve(Boolean.toString(failSave)));
            AtomicInteger writes = new AtomicInteger();
            GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(),
                    () -> ignored -> remote, results -> {
                        if (writes.incrementAndGet() == 1) {
                            // The per-repository summary must wait for this write to succeed.
                            System.out.println("TEST: saving repository results");
                            if (failSave) throw new IllegalStateException("Simulated statistics save failure");
                        }
                    }, ignored -> { }, store::load, store::save);
            ConsoleOutput console = captureConsole(loader::loadData);
            if (failSave) {
                assertFalse(console.out().contains("Repository complete"));
                assertTrue(console.err().contains("[download course/project] Could not save repository results"));
                assertTrue(console.err().contains("Simulated statistics save failure"));
                assertEquals(0, loader.getLastConfirmedRepositoryCount());
            } else {
                assertTrue(console.out().indexOf("TEST: saving repository results") < console.out().indexOf("Repository complete"));
                assertEquals(1, loader.getLastConfirmedRepositoryCount());
                assertTrue(console.err().isEmpty());
            }
            assertEquals(2, writes.get());
            assertNotNull(store.load(URL));
        }
    }
    @Test void updateCheckReportsAnAccessSetupFailureWithoutTouchingOriginals(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir);
        loader(initial(), store, new AtomicInteger()).loadData(); byte[] original = Files.readAllBytes(store.pathFor(URL));
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(),
                () -> { throw new IllegalStateException("Simulated access failure"); }, ignored -> { }, ignored -> { }, store::load, store::save);
        ConsoleOutput console = captureConsole(() -> assertThrows(IllegalStateException.class, () -> loader.checkForUpdates((url, status) -> fail())));
        assertTrue(console.err().contains("[check] Could not prepare GitHub access"));
        assertTrue(console.err().contains("Simulated access failure"));
        assertFalse(console.out().contains("Remote checks finished"));
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
    }
    private static void assertCancellationTrace(ConsoleOutput console) {
        assertTrue(console.out().contains("Remote update checks cancelled after"));
        assertTrue(console.err().isEmpty(), console.err());
        assertFalse(console.out().contains("Remote checks finished"));
    }
    @Test void cancelledListenerStopsChecksWithoutAnErrorCallbackOrFalseSuccess(@TempDir Path dir) throws Exception {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir);
        loader(remote, store, new AtomicInteger()).loadData(); byte[] original = Files.readAllBytes(store.pathFor(URL));
        AtomicInteger requests = new AtomicInteger(), callbacks = new AtomicInteger();
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL, URL + "-other"), ignored -> new AuthorshipConfig(),
                () -> url -> { requests.incrementAndGet(); return remote; }, ignored -> { }, ignored -> { }, store::load, store::save);
        ConsoleOutput console = captureConsole(() -> assertThrows(CancellationException.class, () -> loader.checkForUpdates((url, status) -> {
            callbacks.incrementAndGet(); throw new CancellationException("Superseded check");
        })));
        assertCancellationTrace(console);
        assertFalse(console.out().contains("Check complete"));
        assertEquals(1, callbacks.get(), "Do not call the cancelled listener again with a false error");
        assertEquals(1, requests.get(), "Do not start checking the next repository");
        assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
    }
    @Test void cancelledAccessSetupDoesNotBecomeAnAuthenticationFailure(@TempDir Path dir) {
        AtomicInteger requests = new AtomicInteger(); RawDataStore store = new RawDataStore(dir);
        GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(), () -> {
            requests.incrementAndGet(); throw new CancellationException("Access setup cancelled");
        }, ignored -> { }, ignored -> { }, store::load, store::save);
        ConsoleOutput console = captureConsole(() -> assertThrows(CancellationException.class, () -> loader.checkForUpdates((url, status) -> fail())));
        assertCancellationTrace(console); assertEquals(1, requests.get());
        assertFalse(Files.exists(store.pathFor(URL)));
    }
    @Test void interruptedRequestCancelsButAnOrdinaryIoFailureIsStillReported(@TempDir Path dir) throws Exception {
        RawDataStore store = new RawDataStore(dir);
        loader(initial(), store, new AtomicInteger()).loadData(); byte[] original = Files.readAllBytes(store.pathFor(URL));
        for (boolean interrupted : new boolean[] {false, true}) {
            List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>();
            GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL), ignored -> new AuthorshipConfig(), () -> url -> {
                if (interrupted) Thread.currentThread().interrupt();
                throw new IOException("Simulated request failure");
            }, ignored -> { }, ignored -> { }, store::load, store::save);
            try {
                ConsoleOutput console = captureConsole(() -> {
                    if (interrupted) assertThrows(CancellationException.class, () -> loader.checkForUpdates((url, status) -> updates.add(status)));
                    else loader.checkForUpdates((url, status) -> updates.add(status));
                });
                if (interrupted) {
                    assertCancellationTrace(console); assertTrue(updates.isEmpty());
                    assertTrue(Thread.currentThread().isInterrupted(), "Preserve the worker's interruption flag");
                } else {
                    assertTrue(console.err().contains("Update check failed"));
                    assertTrue(console.err().contains("Simulated request failure"));
                    assertEquals(1, updates.size()); assertEquals(-1, updates.get(0).pendingCommits());
                    assertTrue(console.out().contains("0/1 successful, 1 failed"));
                }
            } finally { Thread.interrupted(); }
            assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
        }
    }
    @Test void interruptionBeforeOrBetweenChecksPreventsFurtherRequests(@TempDir Path dir) {
        for (boolean beforeStart : new boolean[] {false, true}) {
            AtomicInteger requests = new AtomicInteger(), clients = new AtomicInteger(); RawDataStore store = new RawDataStore(dir);
            GitHubDataLoader loader = new GitHubDataLoader(() -> List.of(URL, URL + "-other"), ignored -> new AuthorshipConfig(), () -> {
                clients.incrementAndGet(); return url -> { requests.incrementAndGet(); return initial(); };
            }, ignored -> { }, ignored -> { }, store::load, store::save);
            try {
                ConsoleOutput console = captureConsole(() -> {
                    if (beforeStart) Thread.currentThread().interrupt();
                    assertThrows(CancellationException.class, () -> loader.checkForUpdates((url, status) -> Thread.currentThread().interrupt()));
                });
                assertCancellationTrace(console);
                assertEquals(beforeStart ? 0 : 1, requests.get()); assertEquals(beforeStart ? 0 : 1, clients.get());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertFalse(Files.exists(store.pathFor(URL)));
        }
    }
    @Test void fullDownloadFailureNamesTheFullCommitShaWithoutSavingPartialHistory(@TempDir Path dir) {
        String failedSha = "0123456789abcdef0123456789abcdef01234567";
        Repository remote = initial();
        remote.add(failedSha, A, 1, "a");
        remote.add("head", A, 1, failedSha);
        remote.heads.put("main", "head"); remote.failSha = failedSha;
        RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        ConsoleOutput console = captureConsole(() -> assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL)));
        assertEquals(1, console.out().lines().count());
        assertFalse(console.out().contains("Repository complete"));
        assertTrue(console.err().contains("Could not download commit " + failedSha));
        assertTrue(console.err().contains("Simulated incomplete commit"));
        assertEquals(2, remote.fileReads.size()); assertTrue(remote.fileReads.containsKey(failedSha));
        assertFalse(Files.exists(store.pathFor(URL)), "Never save a partially downloaded history");
    }
    @Test void incrementalMetadataAndFileFailuresIdentifyTheCommitAndKeepTheCheckpoint(@TempDir Path dir) throws Exception {
        String failedSha = "fedcba9876543210fedcba9876543210fedcba98";
        for (boolean metadataFailure : new boolean[] {false, true}) {
            Repository remote = new Repository() {
                @Override public GHCommit getCommit(String sha) {
                    if (metadataFailure && sha.equals(failedSha)) throw new IllegalStateException("Simulated commit metadata failure");
                    return super.getCommit(sha);
                }
            };
            remote.add("a", A, 10); remote.heads.put("main", "a");
            RawDataStore store = new RawDataStore(dir.resolve(Boolean.toString(metadataFailure)));
            GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
            loader.loadData(); byte[] original = Files.readAllBytes(store.pathFor(URL));
            remote.add(failedSha, A, 1, "a"); remote.add("new-head", A, 1, failedSha); remote.heads.put("main", "new-head");
            if (!metadataFailure) remote.failSha = failedSha;
            ConsoleOutput console = captureConsole(() -> assertThrows(IllegalStateException.class, () -> loader.refreshRepository(URL)));
            assertFalse(console.out().contains("Repository complete"));
            assertTrue(console.err().contains("Could not download commit " + failedSha));
            assertTrue(console.err().contains(metadataFailure ? "Simulated commit metadata failure" : "Simulated incomplete commit"));
            assertEquals("a", store.load(URL).branchHeads().get("main"));
            assertArrayEquals(original, Files.readAllBytes(store.pathFor(URL)));
            assertFalse(console.out().contains("Original download saved"));
            assertEquals(1, remote.fileReads.get("a"));
        }
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
        List<GitHubDataLoader.UpdateStatus> updates = new ArrayList<>();
        ConsoleOutput console = captureConsole(() -> loader.checkForUpdates((url, status) -> updates.add(status)));
        assertTrue(console.out().contains("[check course/project]"));
        assertEquals(4, console.out().lines().count(), console.out());
        assertFalse(console.out().contains("comparison IDs read"));
        assertTrue(console.out().contains("501 commits not downloaded"));
        assertTrue(console.out().contains("1/1 successful, 0 failed"));
        assertEquals(501, updates.get(0).pendingCommits()); assertEquals(Map.of("a", 1), remote.fileReads);
        assertTrue(remote.commitReads.isEmpty());
    }
    @Test void failedBackgroundUpdateRetainsUsableLocalProjection(@TempDir Path dir) {
        Repository remote = initial(); RawDataStore store = new RawDataStore(dir); GitHubDataLoader loader = loader(remote, store, new AtomicInteger());
        loader.loadData(); remote.add("bad", B, 20, "a"); remote.heads.put("main", "bad"); remote.failSha = "bad";
        List<RepoStats> stats = new ArrayList<>();
        ConsoleOutput console = captureConsole(() -> stats.addAll(loader.loadData()));
        assertTrue(console.err().contains("[download course/project] Download/analysis failed after"));
        assertTrue(console.err().contains("Simulated incomplete commit"));
        assertFalse(console.out().contains("Repository complete"));
        assertTrue(console.out().contains("0/1 repositories confirmed, 1 not confirmed"));
        assertFalse(console.out().contains("Original download saved"));
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
