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
import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
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
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int COMMIT_PROGRESS_INTERVAL = 25;

    private static void trace(String scope, String message) { trace(System.out, scope, message); }
    private static void traceDetail(String scope, String message) {
        if (Boolean.getBoolean("githubanalyzer.debug")) trace(scope, message);
    }
    private static void trace(PrintStream stream, String scope, String message) {
        // Keep concurrent repositories on separate complete lines, including metadata containing line breaks.
        stream.printf("[%s] [%s] %s%n", LocalTime.now().format(LOG_TIME),
                scope.replaceAll("[\\r\\n\\t]", " "), message.replaceAll("[\\r\\n\\t]", " "));
    }
    private static void traceFailure(String scope, String message, Exception error) {
        StringBuilder reason = new StringBuilder();
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 4; depth++, cause = cause.getCause()) {
            if (depth > 0) reason.append(": ");
            reason.append(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        }
        trace(System.err, scope, message + ": " + reason);
    }
    private static String elapsed(long started) {
        return String.format(java.util.Locale.ROOT, "%.1fs", (System.nanoTime() - started) / 1_000_000_000.0);
    }
    private static String shortSha(String sha) { return sha == null ? "not downloaded" : sha.substring(0, Math.min(12, sha.length())); }

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
        AtomicInteger completedRepositories = new AtomicInteger();
        ExecutorService executorService = null;
        long started = System.nanoTime();

        try {
            int threads = Math.min(MAX_THREADS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            trace("sync", "Starting GitHub synchronization: " + repos.size() + " repositories, up to " + threads + " parallel tasks.");
            executorService = Executors.newFixedThreadPool(threads);

            traceDetail("sync", "Preparing GitHub access…");
            RepositoryReader reader = repos.isEmpty() ? null : apiSource.get();

            List<Future<?>> futures = new ArrayList<>();

            for (String repoUrl : repos) {
                futures.add(executorService.submit(() -> {
                    long repoStarted = System.nanoTime();
                    RepoStats confirmed = null;
                    boolean successful = false;
                    try {
                        confirmed = analyzeRepo(repoUrl, reader);
                        if (confirmed == null) return;
                        synchronized (resultLock) {
                            List<RepoStats> updated = replaceResult(progressiveResults, confirmed);
                            resultWriter.accept(new ArrayList<>(updated));
                            progressiveResults.clear();
                            progressiveResults.addAll(updated);
                        }
                        confirmedRepositories.incrementAndGet();
                        successful = true;
                    } catch (Exception ex) {
                        traceFailure("download " + repositoryName(repoUrl), "Could not save repository results", ex);
                    } finally {
                        synchronized (resultLock) {
                            int completed = completedRepositories.incrementAndGet();
                            if (successful) trace("download " + repositoryName(repoUrl),
                                    "Repository complete (" + completed + "/" + repos.size() + ") in " + elapsed(repoStarted)
                                    + ": " + confirmed.getCommits() + " active commits, " + confirmed.getUserStats().size() + " people.");
                        }
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); }
                catch (Exception e) { traceFailure("sync", "Repository task failed", e); }
            }
            
            lastConfirmedRepositoryCount = confirmedRepositories.get();

        } catch (Exception ex) {
            traceFailure("sync", "GitHub synchronization failed", ex);
        } finally {
            if (executorService != null) executorService.shutdown();
        }

        List<RepoStats> result = readLocalData(false);
        traceDetail("sync", "Writing derived statistics and CSV for " + result.size() + " available repositories…");
        if (!result.isEmpty() || repos.isEmpty()) {
            resultWriter.accept(result);
            csvWriter.accept(result);
        }

        trace("sync", "Synchronization finished in " + elapsed(started) + ": " + lastConfirmedRepositoryCount + "/" + repos.size()
                + " repositories confirmed, " + (repos.size() - lastConfirmedRepositoryCount) + " not confirmed; "
                + result.size() + " local results available.");
        return result;
    }

    private RepoStats analyzeRepo(String repoUrl, RepositoryReader reader) {
        String scope = "download " + repositoryName(repoUrl);
        long started = System.nanoTime();
        trace(scope, "Starting repository download and analysis.");
        try {
            traceDetail(scope, "Reading the last saved original download…");
            RepositorySnapshot previous = snapshotUpdateReader.apply(repoUrl);
            traceDetail(scope, previous == null ? "No usable checkpoint: starting a complete download."
                    : "Incremental update: " + previous.commits().size() + " original commits already stored.");
            traceDetail(scope, "Fetching repository metadata from GitHub…");
            RepositorySnapshot raw = downloadSnapshot(repoUrl, reader.get(repoUrl), message -> traceDetail(scope, message), previous);
            raw.activeCommits();
            if (raw != previous) {
                traceDetail(scope, "Saving complete original download (" + raw.commits().size() + " archived commits)…");
                snapshotWriter.accept(raw);
                traceDetail(scope, "Original download saved; branch checkpoints updated.");
            }
            traceDetail(scope, "Recalculating statistics locally with saved authorship assignments…");
            RepoStats result = calculate(raw, configured(repoUrl), authorshipSource.apply(repoUrl));
            traceDetail(scope, "Ready in " + elapsed(started) + ": " + result.getCommits() + " active commits, "
                    + result.getUserStats().size() + " people, " + result.getCodeLines() + " Java source lines.");
            return result;
        } catch (Exception ex) {
            traceFailure(scope, "Download/analysis failed after " + elapsed(started), ex);
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
        long started = System.nanoTime();
        if (persist) trace("local", "Recalculating " + repos.size() + " repositories from saved originals; no GitHub requests.");
        List<RepoStats> result = new ArrayList<>();
        for (RepositoryConfig config : repositoryConfigs.values()) {
            String scope = "local " + repositoryName(config.url());
            try {
                traceDetail(scope, "Reading original download and authorship assignments…");
                RepositorySnapshot raw = snapshotReader.apply(config.url());
                if (raw != null) {
                    RepoStats stats = calculate(raw, config, authorshipSource.apply(config.url()));
                    result.add(stats);
                    if (persist) trace(scope, "Calculated: " + stats.getCommits() + " active commits, " + stats.getUserStats().size() + " people.");
                } else if (persist) trace(scope, "Pending: no original download; a first GitHub download is required.");
            } catch (IllegalStateException ex) {
                traceFailure(scope, "Local analysis pending", ex);
            }
        }
        Collections.sort(result);
        if (persist && (!result.isEmpty() || repos.isEmpty())) {
            traceDetail("local", "Writing derived statistics and CSV…");
            resultWriter.accept(result); csvWriter.accept(result);
        }
        if (persist) trace("local", "Local recalculation finished in " + elapsed(started) + ": " + result.size() + "/" + repos.size() + " results available.");
        return result;
    }

    public RepoStats recalculateRepository(String url, AuthorshipConfig rules) {
        reloadRepositories();
        if (!repositoryConfigs.containsKey(url)) throw new IllegalStateException("Repository is no longer configured.");
        String scope = "local " + repositoryName(url);
        long started = System.nanoTime();
        trace(scope, "Recalculating authorship from saved originals; no GitHub requests.");
        try {
            RepoStats result = calculate(requireRawData(url), configured(url), rules);
            trace(scope, "Authorship recalculated in " + elapsed(started) + ": " + result.getUserStats().size() + " people, "
                    + result.getCommits() + " active commits.");
            return result;
        } catch (Exception ex) {
            traceFailure(scope, "Local authorship recalculation failed", ex);
            throw ex;
        }
    }

    /** Explicit download entry point; author editing never calls it. */
    public RepoStats refreshRepository(String url) {
        reloadRepositories();
        if (!repositoryConfigs.containsKey(url)) throw new IllegalStateException("Repository is no longer configured.");
        long started = System.nanoTime();
        RepoStats result = analyzeRepo(url, apiSource.get());
        if (result == null) throw new IllegalStateException("Download failed. The previous original download has been kept.");
        trace("download " + repositoryName(url), "Repository complete in " + elapsed(started) + ": "
                + result.getCommits() + " active commits, " + result.getUserStats().size() + " people.");
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
        progress.accept("Reading remote branch heads and repository metadata…");
        Map<String, GHBranch> branches = repository.getBranches();
        Map<String, String> heads = new TreeMap<>();
        branches.forEach((name, branch) -> heads.put(name, branch.getSHA1()));
        Date pushed = repository.getPushedAt();
        String defaultBranch = repository.getDefaultBranch();
        boolean publicRepository = !repository.isPrivate();
        progress.accept("Remote metadata: " + heads.size() + " branches; default=" + defaultBranch + "; visibility=" + (publicRepository ? "public" : "private") + ".");
        if (!hasMetadataChanges(previous, heads, defaultBranch, publicRepository)) {
            progress.accept("Remote heads, default branch and visibility unchanged; reusing original download without fetching commit files.");
            return completeAuthorDates(repository, previous, progress);
        }
        for (var branch : heads.entrySet()) {
            String old = previous == null ? null : previous.branchHeads().get(branch.getKey());
            progress.accept("Branch " + branch.getKey() + ": checkpoint " + shortSha(old) + " → remote " + shortSha(branch.getValue()) + ".");
        }
        if (heads.isEmpty()) progress.accept("No remote branch heads; archived commits will be retained, with no active history.");
        List<SourceFile> files = new ArrayList<>();
        Map<String, GHCommit> unique = new LinkedHashMap<>();
        if (repository.getSize() != 0 || !heads.isEmpty()) {
            if (defaultBranch == null || defaultBranch.isBlank()) throw new IllegalStateException("Missing default branch.");
            String defaultHead = heads.getOrDefault(defaultBranch, defaultBranch);
            if (previous != null && defaultHead.equals(previous.branchHeads().get(previous.defaultBranch()))) {
                files.addAll(previous.sourceFiles());
                progress.accept("Default-branch head unchanged; reusing " + files.size() + " source file entries without downloading contents.");
            }
            else {
                progress.accept("Reading source snapshot for " + defaultBranch + " at " + shortSha(defaultHead) + "…");
                Map<String, SourceFile> oldFiles = new HashMap<>();
                if (previous != null) previous.sourceFiles().forEach(file -> oldFiles.put(file.path(), file));
                downloadFiles(repository.getDirectoryContent("/", defaultHead), "", files, oldFiles, progress);
                long reused = files.stream().filter(file -> oldFiles.get(file.path()) == file).count();
                long downloaded = files.stream().filter(file -> oldFiles.get(file.path()) != file && file.javaContent() != null).count();
                progress.accept("Source snapshot ready: " + files.size() + " file entries; " + downloaded + " Java contents downloaded, " + reused + " unchanged entries reused.");
            }
            if (previous == null && !heads.isEmpty()) {
                String branch = heads.containsKey(defaultBranch) ? defaultBranch : heads.keySet().iterator().next();
                progress.accept("Reading full commit history from branch " + branch + "…");
                int scanned = 0;
                for (GHCommit commit : repository.queryCommits().from(heads.get(branch)).list()) {
                    checkInterrupted();
                    if (commit.getSHA1() == null || commit.getSHA1().isBlank()) throw new IllegalStateException("Missing commit SHA.");
                    unique.putIfAbsent(commit.getSHA1(), commit);
                    if (++scanned % 100 == 0) progress.accept("Branch " + branch + ": " + scanned + " history entries read; " + unique.size() + " unique commits discovered.");
                }
                progress.accept("Branch " + branch + " history read: " + scanned + " entries; reusing this history across all branches.");
            }
        }
        Map<String, Commit> known = new LinkedHashMap<>();
        if (previous != null) previous.commits().forEach(commit -> known.put(commit.sha(), commit));
        int index = 0;
        for (GHCommit commit : unique.values()) {
            ++index;
            if (index == 1 || index % COMMIT_PROGRESS_INTERVAL == 0 || index == unique.size())
                progress.accept("Downloading changed-file details: " + index + "/" + unique.size() + " commits (" + shortSha(commit.getSHA1()) + ")…");
            try {
                Commit raw = downloadCommit(commit, resolveAuthorRaw(commit));
                known.put(raw.sha(), raw);
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) {
                checkInterrupted();
                throw new IllegalStateException("Could not download commit " + commit.getSHA1(), ex);
            }
        }
        // A commit's complete parent list, rather than history-list ordering,
        // determines where branches join. Visit unknown parents exactly once,
        // including every merge parent and histories with unrelated roots.
        var pending = new java.util.ArrayDeque<>(new java.util.LinkedHashSet<>(heads.values()));
        if (previous == null) known.values().forEach(commit -> commit.parents().stream()
                .filter(parent -> !known.containsKey(parent)).forEach(pending::addLast));
        int downloaded = 0;
        while (!pending.isEmpty()) {
            checkInterrupted();
            String sha = pending.removeFirst();
            if (known.containsKey(sha)) continue;
            if (downloaded % COMMIT_PROGRESS_INTERVAL == 0) progress.accept("Downloading new commit #" + (downloaded + 1) + " (" + shortSha(sha) + "), including changed files and unknown parents…");
            checkInterrupted();
            Commit raw;
            try {
                GHCommit commit = repository.getCommit(sha);
                if (!sha.equals(commit.getSHA1())) throw new IllegalStateException("Unexpected commit SHA.");
                raw = downloadCommit(commit, resolveAuthorRaw(commit));
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) {
                checkInterrupted();
                throw new IllegalStateException("Could not download commit " + sha, ex);
            }
            known.put(sha, raw);
            pending.addAll(raw.parents());
            if (++downloaded % COMMIT_PROGRESS_INTERVAL == 0) progress.accept("Shared history: " + downloaded + " new commits complete; " + pending.size() + " history references still to check.");
        }
        List<Commit> commits = new ArrayList<>(known.values());
        if (previous == null) progress.accept("Full history ready: " + commits.size() + " unique commits downloaded with their changed files.");
        else progress.accept("Incremental history ready: " + downloaded + " new commits downloaded; " + previous.commits().size() + " archived originals retained.");
        checkInterrupted();
        RepositorySnapshot downloadedSnapshot = new RepositorySnapshot(RepositorySnapshot.CURRENT_VERSION, url, url.substring(url.lastIndexOf('/') + 1),
                System.currentTimeMillis(), repository.getCreatedAt().getTime(), pushed == null ? -1 : pushed.getTime(),
                publicRepository, defaultBranch == null ? "" : defaultBranch, heads, commits, files);
        return completeAuthorDates(repository, downloadedSnapshot, progress);
    }

    /** Upgrade active legacy commits on explicit refresh only; preserve all downloaded file data. */
    private RepositorySnapshot completeAuthorDates(GHRepository repository, RepositorySnapshot snapshot,
                                                    Consumer<String> progress) throws Exception {
        if (snapshot.commits().stream().noneMatch(commit -> commit.authorTimestamp() == null)) return snapshot;
        List<Commit> missing = snapshot.activeCommits().stream().filter(commit -> commit.authorTimestamp() == null).toList();
        if (missing.isEmpty()) return snapshot;
        Map<String, Commit> replacements = new HashMap<>();
        for (Commit original : missing) {
            checkInterrupted();
            progress.accept("Completing author dates: " + (replacements.size() + 1) + "/" + missing.size() + " (" + shortSha(original.sha()) + "); saved file details reused.");
            throttleHeavyCalls(); checkInterrupted();
            try {
                GHCommit remote = repository.getCommit(original.sha());
                if (!original.sha().equals(remote.getSHA1())) throw new IllegalStateException("Unexpected commit SHA.");
                replacements.put(original.sha(), new Commit(original.sha(), original.author(), original.timestamp(),
                        original.parents(), original.files(), authorDate(remote)));
            } catch (CancellationException ex) {
                throw ex;
            } catch (Exception ex) {
                checkInterrupted();
                throw new IllegalStateException("Could not complete author date for commit " + original.sha(), ex);
            }
        }
        checkInterrupted();
        return new RepositorySnapshot(snapshot.version(), snapshot.url(), snapshot.name(), System.currentTimeMillis(),
                snapshot.createdAt(), snapshot.lastPushTime(), snapshot.publicRepository(), snapshot.defaultBranch(),
                snapshot.branchHeads(), snapshot.commits().stream().map(commit -> replacements.getOrDefault(commit.sha(), commit)).toList(), snapshot.sourceFiles());
    }

    private static long authorDate(GHCommit commit) throws IOException {
        GHCommit.ShortInfo info = commit.getCommitShortInfo();
        GitUser gitAuthor = info == null ? null : info.getAuthor();
        Date authoredAt = gitAuthor == null ? null : gitAuthor.getDate();
        return authoredAt == null ? -1 : authoredAt.getTime();
    }

    private void downloadFiles(List<GHContent> contents, String prefix, List<SourceFile> result, Map<String, SourceFile> oldFiles, Consumer<String> progress) throws Exception {
        for (GHContent content : contents) {
            checkInterrupted();
            String path = content.getPath();
            if (path == null || path.isBlank()) path = prefix + content.getName();
            if (content.isDirectory()) { downloadFiles(content.listDirectoryContent().toList(), path + "/", result, oldFiles, progress); continue; }
            if (result.size() % 25 == 0) progress.accept("Source snapshot: " + result.size() + " file entries processed; reading " + path + "…");
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
        List<ChangedFile> files = new ArrayList<>();
        for (GHCommit.File file : safeListFiles(commit, new StringBuilder()))
            files.add(new ChangedFile(file.getFileName(), file.getPreviousFilename(), file.getLinesAdded(), file.getLinesDeleted()));
        // listFiles populates this commit's metadata. Reading its parent IDs is
        // local; getParents would eagerly fetch each parent's metadata, even at
        // a branch join where we already have the complete original commit.
        List<String> parentShas = new ArrayList<>(commit.getParentSHA1s());
        for (String sha : parentShas) {
            if (sha == null || sha.isBlank()) throw new IllegalStateException("Missing parent SHA.");
        }
        Date committedAt = commit.getCommitDate();
        return new Commit(commit.getSHA1(), author, committedAt == null ? -1 : committedAt.getTime(), parentShas, files,
                authorDate(commit));
    }

    static int countExternalMarkers(String line) { return StatisticsCalculator.countExternalMarkers(line); }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Operation cancelled.");
    }
    public record UpdateStatus(int pendingCommits, boolean headsChanged, String error) { }

    /** Metadata-only check: no changed files, source contents, snapshots or rules are written. */
    public void checkForUpdates(java.util.function.BiConsumer<String, UpdateStatus> listener) {
        long started = System.nanoTime();
        try {
            checkInterrupted();
            reloadRepositories();
            trace("check", "Starting remote update checks for " + repos.size() + " repositories (commit IDs and metadata only; no files downloaded).");
            RepositoryReader reader;
            try { reader = apiSource.get(); }
            catch (CancellationException ex) { throw ex; }
            catch (Exception ex) {
                checkInterrupted();
                traceFailure("check", "Could not prepare GitHub access; update checks not started", ex);
                throw ex;
            }
            int checked = 0, failed = 0;
            for (String url : new ArrayList<>(repos)) {
                checkInterrupted();
                String scope = "check " + repositoryName(url);
                long repoStarted = System.nanoTime();
                trace(scope, "Checking repository " + (++checked) + "/" + repos.size() + ": fetching remote metadata and reading the local checkpoint…");
                try {
                    UpdateStatus status = inspectUpdates(reader.get(url), snapshotReader.apply(url), message -> traceDetail(scope, message));
                    checkInterrupted();
                    listener.accept(url, status);
                    checkInterrupted();
                    trace(scope, "Check complete in " + elapsed(repoStarted) + ": " + status.pendingCommits() + " commits not downloaded; "
                            + (status.headsChanged() ? "remote changes pending." : "no remote changes."));
                } catch (CancellationException ex) {
                    throw ex;
                } catch (Exception ex) {
                    checkInterrupted();
                    failed++;
                    traceFailure(scope, "Update check failed after " + elapsed(repoStarted) + "; saved originals unchanged", ex);
                    listener.accept(url, new UpdateStatus(-1, false, ex.getMessage()));
                }
            }
            trace("check", "Remote checks finished in " + elapsed(started) + ": " + (checked - failed) + "/" + checked + " successful, " + failed + " failed.");
        } catch (CancellationException ex) {
            trace("check", "Remote update checks cancelled after " + elapsed(started) + "; saved originals unchanged.");
            throw ex;
        }
    }

    UpdateStatus inspectUpdates(GHRepository repository, RepositorySnapshot previous) throws Exception {
        return inspectUpdates(repository, previous, message -> { });
    }

    private UpdateStatus inspectUpdates(GHRepository repository, RepositorySnapshot previous, Consumer<String> progress) throws Exception {
        repository.setCompareUsePaginatedCommits(true);
        progress.accept("Reading remote branch heads…");
        Map<String, String> heads = new TreeMap<>();
        repository.getBranches().forEach((name, branch) -> heads.put(name, branch.getSHA1()));
        java.util.Set<String> known = new java.util.HashSet<>();
        if (previous != null) previous.commits().forEach(commit -> known.add(commit.sha()));
        progress.accept("Comparing " + heads.size() + " remote branches against " + known.size() + " stored original commits.");
        java.util.Set<String> missing = new java.util.HashSet<>();
        for (var branch : heads.entrySet()) {
            checkInterrupted();
            String old = previous == null ? null : previous.branchHeads().get(branch.getKey());
            progress.accept("Branch " + branch.getKey() + ": checkpoint " + shortSha(old) + " → remote " + shortSha(branch.getValue()) + ".");
            if (known.contains(branch.getValue())) {
                progress.accept("Branch " + branch.getKey() + ": head already downloaded; no commit comparison needed.");
                continue;
            }
            if (old != null) {
                try {
                    progress.accept("Comparing branch " + branch.getKey() + " with paginated commit IDs…");
                    int scanned = 0;
                    for (GHCommit commit : repository.getCompare(old, branch.getValue()).listCommits()) {
                        checkInterrupted();
                        if (commit.getSHA1() == null) throw new IllegalStateException("Missing comparison SHA.");
                        if (!known.contains(commit.getSHA1())) missing.add(commit.getSHA1());
                        if (++scanned % 100 == 0) progress.accept("Branch " + branch.getKey() + ": " + scanned + " comparison IDs read; " + missing.size() + " distinct commits pending across checked branches.");
                    }
                    progress.accept("Branch " + branch.getKey() + ": comparison complete (" + scanned + " IDs); " + missing.size() + " distinct commits pending so far.");
                    continue;
                } catch (org.kohsuke.github.GHFileNotFoundException ex) {
                    // A rewritten/deleted base may no longer be comparable. Read IDs from the new history.
                    progress.accept("Branch " + branch.getKey() + ": comparison base unavailable; falling back to history IDs.");
                }
            }
            progress.accept("Reading history IDs for branch " + branch.getKey() + " without downloading changed files…");
            int scanned = 0;
            for (GHCommit commit : repository.queryCommits().from(branch.getValue()).list()) {
                checkInterrupted();
                if (commit.getSHA1() == null) throw new IllegalStateException("Missing history SHA.");
                if (!known.contains(commit.getSHA1())) missing.add(commit.getSHA1());
                if (++scanned % 100 == 0) progress.accept("Branch " + branch.getKey() + ": " + scanned + " history IDs read; " + missing.size() + " distinct commits pending so far.");
            }
            progress.accept("Branch " + branch.getKey() + ": history IDs checked (" + scanned + "); " + missing.size() + " distinct commits pending so far.");
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
            // GitHub caps this endpoint at 3,000 files. At the cap completeness
            // cannot be established, so keep the prior snapshot rather than
            // silently accepting truncated contribution metrics.
            if (files.size() >= 3000) throw new IllegalStateException(
                    "Commit " + safeSha(commit) + " reaches GitHub's 3,000-file limit; complete contribution metrics cannot be guaranteed.");
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
                throw new CancellationException("Analysis cancelled.");
            }
        }
    }

    private String safeSha(GHCommit c) {
        try { return (c == null) ? "null" : c.getSHA1(); }
        catch (Exception e) { return "unknown"; }
    }
}
