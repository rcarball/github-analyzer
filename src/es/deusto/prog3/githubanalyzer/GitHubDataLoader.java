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

package es.deusto.prog3.githubanalyzer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitUser;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorSummary;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.RepositoryConfig;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.ChangedFile;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.SourceFile;
import es.deusto.prog3.githubanalyzer.persistence.AuthorshipStore;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;
import es.deusto.prog3.githubanalyzer.persistence.RawDataStore;

public class GitHubDataLoader {

    private static final GitHubDataLoader instance = new GitHubDataLoader();
    private final Supplier<List<String>> repositorySource;
    private final Function<String, AuthorshipConfig> authorshipSource;
    @FunctionalInterface
    interface RepositoryReader { GHRepository get(String url) throws IOException; }
    private final Supplier<RepositoryReader> apiSource;
    private final Consumer<List<RepoStats>> resultWriter;
    private final Consumer<List<RepoStats>> csvWriter;
    private final Function<String, RepositorySnapshot> snapshotReader;
    private final Function<String, RepositorySnapshot> snapshotUpdateReader;
    private final Consumer<RepositorySnapshot> snapshotWriter;
    private final Map<String, RepositorySnapshot> memorySnapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Map<String, RepositoryConfig> repositoryConfigs = Map.of();
    private volatile List<String> repos = Collections.emptyList();
    // Successful repositories in the most recent full analysis.
    private volatile int lastConfirmedRepositoryCount;

    // Repositories are independent, so they can be analyzed in parallel. Calls
    // that expand a commit into its changed files are much more expensive, hence
    // the stricter shared limit below.
    private static final int MAX_THREADS = 4;
    private static final int MAX_CONCURRENT_LIST_FILES = 2;
    private static final Semaphore LIST_FILES_SEMAPHORE = new Semaphore(MAX_CONCURRENT_LIST_FILES);

    // A best-effort pause between expensive API calls. Together with the semaphore
    // it reduces bursts without serializing the complete repository analysis.
    private static final long MIN_DELAY_BETWEEN_HEAVY_CALLS_MS = 120;
    private static final AtomicLong LAST_HEAVY_CALL_TS = new AtomicLong(0);

    private GitHubDataLoader() {
        this(() -> Configurator.getInstance().getRepositories());
    }

    // Read and validate the repository list before starting an analysis.
    GitHubDataLoader(Supplier<List<String>> repositorySource) {
        this(repositorySource, url -> new AuthorshipStore(Configurator.getInstance().getAuthorshipDirectory()).load(url));
    }

    GitHubDataLoader(Supplier<List<String>> repositorySource, Function<String, AuthorshipConfig> authorshipSource) {
        this(repositorySource, authorshipSource, GitHubDataLoader::openReader,
                data -> DataManager.getInstance().storeData(data), data -> DataManager.getInstance().storeCSV(data),
                url -> new RawDataStore(Configurator.getInstance().getRawDataDirectory()).load(url),
                raw -> new RawDataStore(Configurator.getInstance().getRawDataDirectory()).save(raw),
                url -> new RawDataStore(Configurator.getInstance().getRawDataDirectory()).loadForUpdate(url));
    }

    GitHubDataLoader(Supplier<List<String>> repositorySource, Function<String, AuthorshipConfig> authorshipSource,
                     Supplier<RepositoryReader> apiSource, Consumer<List<RepoStats>> resultWriter,
                     Consumer<List<RepoStats>> csvWriter) {
        this(repositorySource, authorshipSource, apiSource, resultWriter, csvWriter, null, null);
    }

    GitHubDataLoader(Supplier<List<String>> repositorySource, Function<String, AuthorshipConfig> authorshipSource,
                     Supplier<RepositoryReader> apiSource, Consumer<List<RepoStats>> resultWriter,
                     Consumer<List<RepoStats>> csvWriter, Function<String, RepositorySnapshot> snapshotReader,
                     Consumer<RepositorySnapshot> snapshotWriter) {
        this(repositorySource, authorshipSource, apiSource, resultWriter, csvWriter, snapshotReader, snapshotWriter, null);
    }

    GitHubDataLoader(Supplier<List<String>> repositorySource, Function<String, AuthorshipConfig> authorshipSource,
                     Supplier<RepositoryReader> apiSource, Consumer<List<RepoStats>> resultWriter,
                     Consumer<List<RepoStats>> csvWriter, Function<String, RepositorySnapshot> snapshotReader,
                     Consumer<RepositorySnapshot> snapshotWriter, Function<String, RepositorySnapshot> snapshotUpdateReader) {
        this.repositorySource = repositorySource;
        this.authorshipSource = authorshipSource;
        this.apiSource = apiSource;
        this.resultWriter = resultWriter;
        this.csvWriter = csvWriter;
        this.snapshotReader = snapshotReader == null ? memorySnapshots::get : snapshotReader;
        this.snapshotUpdateReader = snapshotUpdateReader == null ? this.snapshotReader : snapshotUpdateReader;
        this.snapshotWriter = snapshotWriter == null ? raw -> memorySnapshots.put(raw.url(), raw) : snapshotWriter;
    }

    private static RepositoryReader openReader() {
        try {
            GitHub github = new GitHubBuilder().withOAuthToken(Configurator.getInstance().getGithubToken()).build();
            return url -> github.getRepository(repositoryName(url));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize GitHub access.", ex);
        }
    }

    /** Validate the complete input before replacing the last known configuration. */
    public synchronized void reloadRepositories() {
        List<RepositoryConfig> parsed;
        try {
            parsed = RepositoryConfig.parseLines(repositorySource.get());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        Map<String, RepositoryConfig> configs = new LinkedHashMap<>();
        for (RepositoryConfig config : parsed) configs.put(config.url(), config);
        repositoryConfigs = Collections.unmodifiableMap(configs);
        repos = new ArrayList<>(configs.keySet());
    }

    public List<RepositoryConfig> getRepositoryConfigs() { return new ArrayList<>(repositoryConfigs.values()); }

    public static GitHubDataLoader getInstance() {
        return instance;
    }

    /** Number of distinct repositories configured for analysis. */
    public int getConfiguredRepositoryCount() {
        return repos.size();
    }

    /** Number of repositories actually confirmed by the most recent load. */
    public int getLastConfirmedRepositoryCount() {
        return lastConfirmedRepositoryCount;
    }

    /** Compatibility entry points: previous results are deliberately ignored. */
    public List<RepoStats> loadData(List<RepoStats> ignored) { return loadData(); }
    public List<RepoStats> loadData(List<RepoStats> ignored, boolean forceRefresh) { return loadData(); }

    public synchronized List<RepoStats> loadData() {
        reloadRepositories();
        lastConfirmedRepositoryCount = 0;
        List<RepoStats> progressiveResults = new ArrayList<>();
        Object resultLock = new Object();
        AtomicInteger confirmedRepositories = new AtomicInteger();
        ExecutorService executorService = null;

        try {
            System.out.printf("- Analyzing %d repositories...\n\n", repos.size());

            int threads = Math.min(MAX_THREADS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            executorService = Executors.newFixedThreadPool(threads);

            RepositoryReader reader = repos.isEmpty() ? null : apiSource.get();

            List<Future<?>> futures = new ArrayList<>();

            for (String repoUrl : repos) {
                futures.add(executorService.submit(() -> {
                    RepoStats confirmed = analyzeRepo(repoUrl, reader);
                    if (confirmed == null) return;

                    // Save each completed repository together with this run's other
                    // fresh results; older runs are never read or mixed in.
                    synchronized (resultLock) {
                        List<RepoStats> updated = replaceResult(progressiveResults, confirmed);
                        progressiveResults.clear();
                        progressiveResults.addAll(updated);
                        resultWriter.accept(new ArrayList<>(progressiveResults));
                    }
                    confirmedRepositories.incrementAndGet();
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); }
                catch (Exception e) { System.err.println("\t* Error waiting for task: " + e.getMessage()); }
            }
            
            lastConfirmedRepositoryCount = confirmedRepositories.get();

        } catch (Exception ex) {
            System.err.printf("\t* Error getting info from GitHub: %s\n\n", ex.getMessage());
        } finally {
            if (executorService != null) executorService.shutdown();
        }

        List<RepoStats> result = readLocalData(false);
        if (!result.isEmpty() || repos.isEmpty()) resultWriter.accept(result);
        csvWriter.accept(result);

        System.out.printf("- %d of %d repositories confirmed; fresh results saved progressively\n\n",
                lastConfirmedRepositoryCount, repos.size());
        return result;
    }

    private RepoStats analyzeRepo(String repoUrl, RepositoryReader reader) {
        try {
            RepositorySnapshot previous = snapshotUpdateReader.apply(repoUrl);
            RepositorySnapshot raw = downloadSnapshot(repoUrl, reader.get(repoUrl), message -> { }, previous);
            raw.activeCommits();
            if (raw != previous) snapshotWriter.accept(raw);
            return calculate(raw, configured(repoUrl), authorshipSource.apply(repoUrl));
        } catch (Exception ex) {
            System.err.printf("* Could not download/analyze '%s': %s%n", repoUrl, ex.getMessage());
            return null;
        }
    }

    // Compatibility test entry point: never persists fixtures into the user's raw directory.
    RepoStats analyzeRepository(String url, GHRepository repository, List<RepoStats> ignored, boolean forceRefresh) {
        try { return calculate(downloadSnapshot(url, repository, message -> { }), configured(url), authorshipSource.apply(url)); }
        catch (Exception ex) { System.err.println("* Incomplete analysis: " + ex.getMessage()); return null; }
    }

    private RepositoryConfig configured(String url) {
        return repositoryConfigs.getOrDefault(url, new RepositoryConfig(url, "", null));
    }
    private static RepoStats calculate(RepositorySnapshot raw, RepositoryConfig config, AuthorshipConfig rules) {
        return StatisticsCalculator.calculate(raw, config, rules, ContributionMetrics::defaultRole);
    }

    public RepositorySnapshot requireRawData(String url) {
        RepositorySnapshot raw = snapshotReader.apply(url);
        if (raw == null) throw new IllegalStateException("No original download is available. Use Refresh from GitHub once before managing authorship.");
        return raw;
    }

    /** Rebuild derived results from saved original data, without initializing a GitHub client. */
    public synchronized List<RepoStats> loadLocalData() { return readLocalData(true); }

    private List<RepoStats> readLocalData(boolean persist) {
        reloadRepositories();
        List<RepoStats> result = new ArrayList<>();
        for (RepositoryConfig config : repositoryConfigs.values()) {
            try {
                RepositorySnapshot raw = snapshotReader.apply(config.url());
                if (raw != null) result.add(calculate(raw, config, authorshipSource.apply(config.url())));
            } catch (IllegalStateException ex) {
                System.err.println("* Local analysis pending for " + config.url() + ": " + ex.getMessage());
            }
        }
        Collections.sort(result);
        if (persist && (!result.isEmpty() || repos.isEmpty())) { resultWriter.accept(result); csvWriter.accept(result); }
        return result;
    }

    public RepoStats recalculateRepository(String url, AuthorshipConfig rules) {
        reloadRepositories();
        if (!repositoryConfigs.containsKey(url)) throw new IllegalStateException("Repository is no longer configured.");
        return calculate(requireRawData(url), configured(url), rules);
    }

    /** Explicit download entry point; author editing never calls it. */
    public RepoStats refreshRepository(String url) {
        reloadRepositories();
        if (!repositoryConfigs.containsKey(url)) throw new IllegalStateException("Repository is no longer configured.");
        RepoStats result = analyzeRepo(url, apiSource.get());
        if (result == null) throw new IllegalStateException("Download failed. The previous original download has been kept.");
        return result;
    }

    public List<AuthorSummary> discoverAuthors(String url) {
        checkInterrupted();
        return StatisticsCalculator.authors(requireRawData(url));
    }
    public List<AuthorSummary> discoverAuthors(String url, Consumer<String> progress) {
        checkInterrupted();
        progress.accept("Reading authors from the local original download…");
        return discoverAuthors(url);
    }

    RepositorySnapshot downloadSnapshot(String url, GHRepository repository, Consumer<String> progress) throws Exception {
        return downloadSnapshot(url, repository, progress, null);
    }

    RepositorySnapshot downloadSnapshot(String url, GHRepository repository, Consumer<String> progress, RepositorySnapshot previous) throws Exception {
        checkInterrupted();
        Map<String, GHBranch> branches = repository.getBranches();
        Map<String, String> heads = new TreeMap<>();
        branches.forEach((name, branch) -> heads.put(name, branch.getSHA1()));
        Date pushed = repository.getPushedAt();
        String defaultBranch = repository.getDefaultBranch();
        boolean publicRepository = !repository.isPrivate();
        if (!hasMetadataChanges(previous, heads, defaultBranch, publicRepository)) return previous;
        List<SourceFile> files = new ArrayList<>();
        Map<String, GHCommit> unique = new LinkedHashMap<>();
        if (repository.getSize() != 0 || !heads.isEmpty()) {
            if (defaultBranch == null || defaultBranch.isBlank()) throw new IllegalStateException("Missing default branch.");
            progress.accept("Downloading the default-branch source snapshot…");
            String defaultHead = heads.getOrDefault(defaultBranch, defaultBranch);
            if (previous != null && defaultHead.equals(previous.branchHeads().get(previous.defaultBranch()))) files.addAll(previous.sourceFiles());
            else {
                Map<String, SourceFile> oldFiles = new HashMap<>();
                if (previous != null) previous.sourceFiles().forEach(file -> oldFiles.put(file.path(), file));
                downloadFiles(repository.getDirectoryContent("/", defaultHead), "", files, oldFiles);
            }
            if (previous == null) for (var branch : heads.entrySet()) {
                progress.accept("Downloading commits from " + branch.getKey() + "…");
                for (GHCommit commit : repository.queryCommits().from(branch.getValue()).list()) {
                    checkInterrupted();
                    if (commit.getSHA1() == null || commit.getSHA1().isBlank()) throw new IllegalStateException("Missing commit SHA.");
                    unique.putIfAbsent(commit.getSHA1(), commit);
                }
            }
        }
        List<Commit> commits = new ArrayList<>();
        if (previous != null) {
            Map<String, Commit> known = new LinkedHashMap<>();
            previous.commits().forEach(commit -> known.put(commit.sha(), commit));
            var pending = new java.util.ArrayDeque<>(heads.values());
            while (!pending.isEmpty()) {
                checkInterrupted();
                String sha = pending.removeFirst();
                if (known.containsKey(sha)) continue;
                progress.accept("Downloading new commit " + sha + "…");
                GHCommit commit = repository.getCommit(sha);
                if (!sha.equals(commit.getSHA1())) throw new IllegalStateException("Unexpected commit SHA.");
                Commit raw = downloadCommit(commit, resolveAuthorRaw(commit));
                known.put(sha, raw);
                pending.addAll(raw.parents());
            }
            commits.addAll(known.values());
        }
        int index = 0;
        for (GHCommit commit : unique.values()) {
            progress.accept(String.format("Downloading commit files: %d of %d…", ++index, unique.size()));
            commits.add(downloadCommit(commit, resolveAuthorRaw(commit)));
        }
        checkInterrupted();
        return new RepositorySnapshot(RepositorySnapshot.CURRENT_VERSION, url, url.substring(url.lastIndexOf('/') + 1),
                System.currentTimeMillis(), repository.getCreatedAt().getTime(), pushed == null ? -1 : pushed.getTime(),
                publicRepository, defaultBranch == null ? "" : defaultBranch, heads, commits, files);
    }

    private void downloadFiles(List<GHContent> contents, String prefix, List<SourceFile> result, Map<String, SourceFile> oldFiles) throws Exception {
        for (GHContent content : contents) {
            checkInterrupted();
            String path = content.getPath();
            if (path == null || path.isBlank()) path = prefix + content.getName();
            if (content.isDirectory()) { downloadFiles(content.listDirectoryContent().toList(), path + "/", result, oldFiles); continue; }
            String blob = content.getSha();
            SourceFile old = oldFiles.get(path);
            if (old != null && blob != null && !blob.isBlank() && blob.equals(old.blobSha())) { result.add(old); continue; }
            String text = null;
            if (path.toLowerCase(java.util.Locale.ROOT).endsWith(".java")) {
                try (var input = content.read()) { text = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
            }
            result.add(new SourceFile(path, text, blob));
        }
    }

    Commit downloadCommit(GHCommit commit, AuthorIdentity author) throws Exception {
        checkInterrupted();
        List<GHCommit> parents = commit.getParents();
        List<String> parentShas = new ArrayList<>();
        if (parents != null) for (GHCommit parent : parents) {
            String sha = parent.getSHA1();
            if (sha == null) throw new IllegalStateException("Missing parent SHA.");
            parentShas.add(sha);
        }
        List<ChangedFile> files = new ArrayList<>();
        for (GHCommit.File file : safeListFiles(commit, new StringBuilder()))
            files.add(new ChangedFile(file.getFileName(), file.getPreviousFilename(), file.getLinesAdded(), file.getLinesDeleted()));
        return new Commit(commit.getSHA1(), author, commit.getCommitDate().getTime(), parentShas, files);
    }

    static int countExternalMarkers(String line) { return StatisticsCalculator.countExternalMarkers(line); }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Operation cancelled.");
    }
    public record UpdateStatus(int pendingCommits, boolean headsChanged, String error) { }

    /** Metadata-only check: no changed files, source contents, snapshots or rules are written. */
    public void checkForUpdates(java.util.function.BiConsumer<String, UpdateStatus> listener) {
        reloadRepositories();
        RepositoryReader reader = apiSource.get();
        for (String url : new ArrayList<>(repos)) {
            checkInterrupted();
            try { listener.accept(url, inspectUpdates(reader.get(url), snapshotReader.apply(url))); }
            catch (Exception ex) { listener.accept(url, new UpdateStatus(-1, false, ex.getMessage())); }
        }
    }

    UpdateStatus inspectUpdates(GHRepository repository, RepositorySnapshot previous) throws Exception {
        repository.setCompareUsePaginatedCommits(true);
        Map<String, String> heads = new TreeMap<>();
        repository.getBranches().forEach((name, branch) -> heads.put(name, branch.getSHA1()));
        java.util.Set<String> known = new java.util.HashSet<>();
        if (previous != null) previous.commits().forEach(commit -> known.add(commit.sha()));
        java.util.Set<String> missing = new java.util.HashSet<>();
        for (var branch : heads.entrySet()) {
            checkInterrupted();
            String old = previous == null ? null : previous.branchHeads().get(branch.getKey());
            if (known.contains(branch.getValue())) continue;
            if (old != null) {
                try {
                    for (GHCommit commit : repository.getCompare(old, branch.getValue()).listCommits()) {
                        checkInterrupted();
                        if (commit.getSHA1() == null) throw new IllegalStateException("Missing comparison SHA.");
                        if (!known.contains(commit.getSHA1())) missing.add(commit.getSHA1());
                    }
                    continue;
                } catch (org.kohsuke.github.GHFileNotFoundException ex) {
                    // A rewritten/deleted base may no longer be comparable. Read IDs from the new history.
                }
            }
            for (GHCommit commit : repository.queryCommits().from(branch.getValue()).list()) {
                checkInterrupted();
                if (commit.getSHA1() == null) throw new IllegalStateException("Missing history SHA.");
                if (!known.contains(commit.getSHA1())) missing.add(commit.getSHA1());
            }
        }
        return new UpdateStatus(missing.size(), hasMetadataChanges(previous, heads, repository.getDefaultBranch(), !repository.isPrivate()), null);
    }

    /** Keep the metadata-only check and the download's reuse decision consistent. */
    private static boolean hasMetadataChanges(RepositorySnapshot previous, Map<String, String> heads,
                                               String defaultBranch, boolean publicRepository) {
        return previous == null || !heads.equals(previous.branchHeads())
                || !previous.defaultBranch().equals(defaultBranch == null ? "" : defaultBranch)
                || previous.publicRepository() != publicRepository;
    }

    private static String repositoryName(String url) { return java.net.URI.create(url).getPath().replaceFirst("^/", ""); }

    /**
     * Replaces one result within the current analysis, retaining other fresh results.
     * Package-visible for a focused unit test.
     */
    static List<RepoStats> replaceResult(List<RepoStats> results, RepoStats confirmed) {
        List<RepoStats> updated = new ArrayList<>();
        if (confirmed == null) return updated;

        boolean replaced = false;
        String url = confirmed.getUrl();
        if (results != null) {
            for (RepoStats existing : results) {
                if (existing != null && java.util.Objects.equals(url, existing.getUrl())) {
                    if (!replaced) updated.add(confirmed);
                    replaced = true;
                } else if (existing != null) {
                    updated.add(existing);
                }
            }
        }
        if (!replaced) updated.add(confirmed);
        return updated;
    }

    AuthorIdentity resolveAuthorRaw(GHCommit c) {
        String name = "unknown";
        String email = "";
        String login = null;

        try {
            GHUser ghAuthor = c.getAuthor();
            if (ghAuthor != null) {
                // getLogin() is inline in the commit-list payload. We deliberately do
                // NOT call ghAuthor.getEmail()/getName(): those fields are not included
                // inline, so each call would fetch the full user (one extra API request
                // per commit, quickly exhausting the rate limit on large repos). The git
                // author email/name below come inline from the commit itself.
                login = ghAuthor.getLogin();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Incomplete commit author", ex);
        }

        try {
            GitUser authorInfo = c.getCommitShortInfo().getAuthor();
            if (authorInfo != null) {
                if (authorInfo.getName() != null) name = authorInfo.getName();
                if (authorInfo.getEmail() != null) email = authorInfo.getEmail();
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Incomplete commit author", ex);
        }

        return new AuthorIdentity(name, email, login);
    }

    /**
     * Expands one commit under the shared API budget. A failed expansion is
     * propagated to reject this repository's incomplete result. Other repositories
     * can still finish and persist their own confirmed results.
     */
    private List<GHCommit.File> safeListFiles(GHCommit commit, StringBuilder buffer) {
        boolean acquired = false;
        try {
            LIST_FILES_SEMAPHORE.acquire();
            acquired = true;

            throttleHeavyCalls();
            checkInterrupted();
            List<GHCommit.File> files = commit.listFiles().toList();
            checkInterrupted();
            return files;

        } catch (Exception ex) {
            buffer.append(String.format("\t  - Error listFiles() for commit %s: %s\n",
                    safeSha(commit), ex.getMessage()));
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not read commit files", ex);
        } finally {
            if (acquired) LIST_FILES_SEMAPHORE.release();
        }
    }

    private void throttleHeavyCalls() {
        long now = System.currentTimeMillis();
        long last = LAST_HEAVY_CALL_TS.getAndSet(now);
        long delta = now - last;
        if (last != 0 && delta < MIN_DELAY_BETWEEN_HEAVY_CALLS_MS) {
            try {
                Thread.sleep(MIN_DELAY_BETWEEN_HEAVY_CALLS_MS - delta);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new java.util.concurrent.CancellationException("Analysis cancelled.");
            }
        }
    }

    private String safeSha(GHCommit c) {
        try { return (c == null) ? "null" : c.getSHA1(); }
        catch (Exception e) { return "unknown"; }
    }
}
