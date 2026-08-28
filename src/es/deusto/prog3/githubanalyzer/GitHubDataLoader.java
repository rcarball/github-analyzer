/**
 * This code was developed with AI assistance (ChatGPT) and has been reviewed and validated for correctness.
 */

package es.deusto.prog3.githubanalyzer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitUser;
import org.kohsuke.github.PagedIterable;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.SimpleGitUser;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.persistence.Configurator;
import es.deusto.prog3.githubanalyzer.persistence.DataManager;

public class GitHubDataLoader {

    private static final GitHubDataLoader instance = new GitHubDataLoader();
    private final Map<String, String> groupsRepoMap = new HashMap<>();
    private final List<String> repos;

    private static final Pattern EXTERNAL_PATTERN = Pattern.compile("IAG|FUENTE-EXTERNA");
    private static final String LINES_KEY = "LINES";
    private static final String REF_KEY = "REF";
    private static final String JAVA_EXTENSION = ".java";

    private static final int MAX_THREADS = 4;                 
    private static final int MAX_CONCURRENT_LIST_FILES = 2; 
    private static final Semaphore LIST_FILES_SEMAPHORE = new Semaphore(MAX_CONCURRENT_LIST_FILES);

    private static final long MIN_DELAY_BETWEEN_HEAVY_CALLS_MS = 120;
    private static final AtomicLong LAST_HEAVY_CALL_TS = new AtomicLong(0);

    private GitHubDataLoader() {
    	List<String> originalRepos = Configurator.getInstance().getRepositories();

    	if (originalRepos != null) {

    	    LinkedHashSet<String> uniqueRepos = new LinkedHashSet<>();

    	    for (String line : originalRepos) {
    	        if (line == null) continue;

    	        String trimmed = line.trim();
    	        if (trimmed.isEmpty()) continue;

    	        String[] parts = trimmed.split(";", 2);
    	        String repoUrl = parts[0].trim();

    	        if (repoUrl.isEmpty()) continue;

    	        uniqueRepos.add(repoUrl);

    	        // Mapa: repoURL -> groupCode
    	        if (parts.length == 2) {
    	            String groupCode = parts[1].trim();
    	            if (!groupCode.isEmpty()) {
    	                groupsRepoMap.putIfAbsent(repoUrl, groupCode);
    	            }
    	        }
    	    }

    	    this.repos = new ArrayList<>(uniqueRepos);

    	} else {
    	    this.repos = Collections.emptyList();
    	}
    }

    public static GitHubDataLoader getInstance() {
        return instance;
    }

    public List<RepoStats> loadData(List<RepoStats> statsMap) {
        return loadData(statsMap, false);
    }

    public List<RepoStats> loadData(List<RepoStats> initialStats, boolean forceRefresh) {
        if (initialStats == null) initialStats = new ArrayList<>();

        List<RepoStats> result = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executorService = null;

        try {
            System.out.printf("- Analyzing %d repositories...\n\n", repos.size());

            int threads = Math.min(MAX_THREADS, Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
            executorService = Executors.newFixedThreadPool(threads);

            GitHub github = new GitHubBuilder()
                    .withOAuthToken(Configurator.getInstance().getGithubToken())
                    .build();

            final List<RepoStats> finalStatsMap = initialStats;
            List<Future<?>> futures = new ArrayList<>();

            for (String repoUrl : repos) {
                futures.add(executorService.submit(() -> analyzeRepo(repoUrl, github, finalStatsMap, forceRefresh, result)));
            }

            for (Future<?> f : futures) {
                try { f.get(); }
                catch (Exception e) { System.err.println("\t* Error waiting for task: " + e.getMessage()); }
            }
            
            // Persist the CSV once, from the in-memory result (sorted for stable output).
            // The binary cache is written once by the caller (Main / refresh worker), so
            // we no longer rewrite the whole cache file once per repository (was O(n^2)).
            Collections.sort(result);
            result.forEach(r -> Collections.sort(r.getUserStats()));
            DataManager.getInstance().storeCSV(result);

            System.out.printf("- %d repositories successfully analyzed\n\n", result.size());

        } catch (Exception ex) {
            System.err.printf("\t* Error getting info from GitHub: %s\n\n", ex.getMessage());
        } finally {
            if (executorService != null) executorService.shutdown();
        }

        Collections.sort(result);
        result.forEach(r -> Collections.sort(r.getUserStats()));
        return result;
    }

    private void analyzeRepo(String repoUrl,
                             GitHub github,
                             List<RepoStats> cache,
                             boolean forceRefresh,
                             List<RepoStats> result) {

        StringBuilder buffer = null;

        try {
            String[] tokens = repoUrl.split("/");
            String owner = tokens[tokens.length - 2];
            String repoName = tokens[tokens.length - 1];

            GHRepository repository = github.getRepository(owner + "/" + repoName);
            buffer = new StringBuilder(String.format("- Analyzing repository: %s ...\n", repository.getFullName()));
                    	
            if (forceRefresh) {
        	    buffer.append("\t* Force refresh enabled (ignoring cache)\n");
        	}
            
            RepoStats oldRepoStats = cache.stream()
                    .filter(r -> repoUrl.equals(r.getUrl()))
                    .findFirst()
                    .orElse(null);

            Date pushedAt = repository.getPushedAt();
            long lastPushTime = (pushedAt != null) ? pushedAt.getTime() : -1;
            
            if (oldRepoStats != null && !forceRefresh && oldRepoStats.getLastPushTime() == lastPushTime) {
                result.add(oldRepoStats);
                
                // Update group info if needed
                String group = groupsRepoMap.get(repoUrl);
                
                if (group != null && !group.equals(oldRepoStats.getGroup())) {
                	oldRepoStats.setGroup(group);
                }
                
                buffer.append(String.format("\t* %s repository has not changed (using cache).\n", repoUrl));
                return;
            }

            RepoStats repoStats = new RepoStats();
            String group = groupsRepoMap.get(repoUrl);

            if (group != null) {
            	repoStats.setGroup(groupsRepoMap.get(repoUrl));
			} else {
				repoStats.setGroup("");
			}

            repoStats.setUrl(repoUrl);
            repoStats.setName(repoName);
            repoStats.setLastPushTime(lastPushTime);

            Map<String, GHBranch> branches = repository.getBranches();
            repoStats.setBranches(branches.size());
            repoStats.setCreationDate(repository.getCreatedAt().getTime());
            repoStats.setPublic(!repository.isPrivate());

            if (repository.getSize() == 0) {
                buffer.append(String.format("\t* %s repository is empty :(\n", repoUrl));
                repoStats.setCodeLines(0);
                repoStats.setExternalReferences(0);
                repoStats.setLinesAdded(0);
                repoStats.setLinesDeleted(0);
                repoStats.setLinesChanged(0);
                repoStats.setCommits(0);
                repoStats.setFirstCommit(-1);
                repoStats.setLastCommit(-1);
                result.add(repoStats);
                return;
            }

            Map<String, Integer> fileCounters = filesStatisticsAllBranches(repository, branches, buffer);

            repoStats.setCodeLines(fileCounters.getOrDefault(LINES_KEY, 0));
            fileCounters.remove(LINES_KEY);
            repoStats.setExternalReferences(fileCounters.getOrDefault(REF_KEY, 0));
            fileCounters.remove(REF_KEY);
            repoStats.setFileTypeMap(fileCounters);

            Map<RawIdentity, List<GHCommit>> rawCommits = collectCommitsAllBranches(repository, branches, repoStats, buffer);
        	Map<SimpleGitUser, List<GHCommit>> commitsPerUser = resolveAndMergeAuthors(rawCommits);
        	        	
        	fillUserStatsFromCommits(commitsPerUser, repoStats, buffer);
        	
            repoStats.setLinesAdded(repoStats.getUserStats().stream().mapToInt(UserStats::getAdded).sum());
            repoStats.setLinesDeleted(repoStats.getUserStats().stream().mapToInt(UserStats::getDeleted).sum());
            repoStats.setLinesChanged(repoStats.getUserStats().stream().mapToInt(UserStats::getChanged).sum());            

            result.add(repoStats);
        } catch (Exception e) {
            System.err.printf("\t* Error analyzing '%s': %s\n\n", repoUrl, e.getMessage());
            e.printStackTrace();
        } finally {
            if (buffer != null) System.out.println(buffer);
        }
    }

    // ---------------- FILES ----------------

    private Map<String, Integer> filesStatisticsAllBranches(GHRepository repository,
                                                           Map<String, GHBranch> branches,
                                                           StringBuilder buffer) {
        Map<String, Integer> result = new TreeMap<>();
        result.put(LINES_KEY, 0);
        result.put(REF_KEY, 0);

        Set<String> processedFiles = new HashSet<>();

        for (Map.Entry<String, GHBranch> branchEntry : branches.entrySet()) {
            GHBranch branch = branchEntry.getValue();
            try {
                List<GHContent> allFiles = repository.getDirectoryContent("/", branch.getName());
                processFilesFromBranch(allFiles, result, processedFiles, buffer);
            } catch (Exception ex) {
                buffer.append(String.format("\t* Error processing files from branch '%s': %s\n",
                        branchEntry.getKey(), ex.getMessage()));
            }
        }
        return result;
    }

    private void processFilesFromBranch(List<GHContent> contentList,
                                        Map<String, Integer> result,
                                        Set<String> processedFiles,
                                        StringBuilder buffer) {
        for (GHContent content : contentList) {
            fileStatisticsWithTracking(result, content, processedFiles, buffer);
        }
    }

    private void fileStatisticsWithTracking(Map<String, Integer> counters,
                                            GHContent content,
                                            Set<String> processedFiles,
                                            StringBuilder buffer) {
        try {
            if (content.isDirectory()) {
                for (GHContent c : content.listDirectoryContent()) {
                    fileStatisticsWithTracking(counters, c, processedFiles, buffer);
                }
                return;
            }

            String name = content.getName();
            if (name == null || !name.contains(".")) return;

            String sha = content.getSha();
            if (sha != null && processedFiles.contains(sha)) return;
            if (sha != null) processedFiles.add(sha);

            String type = name.substring(name.lastIndexOf(".")).toLowerCase();
            counters.put(type, counters.getOrDefault(type, 0) + 1);

            if (name.toLowerCase().endsWith(JAVA_EXTENSION)) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(content.read()))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        counters.put(LINES_KEY, counters.get(LINES_KEY) + 1);

                        Matcher matcher = EXTERNAL_PATTERN.matcher(line);
                        while (matcher.find()) {
                            counters.put(REF_KEY, counters.get(REF_KEY) + 1);
                        }
                    }
                } catch (Exception ex) {
                    buffer.append(String.format("\t* Error processing file '%s': %s\n", name, ex.getMessage()));
                }
            }
        } catch (Exception ex) {
            buffer.append(String.format("\t* Error processing content '%s': %s\n",
                    safeContentName(content), ex.getMessage()));
        }
    }

    private String safeContentName(GHContent c) {
        try { return (c == null) ? "null" : c.getName(); }
        catch (Exception e) { return "unknown"; }
    }

    // Package-visible for unit testing of the identity-merging logic.
    static class RawIdentity {
        final String name;
        final String email;
        final String login;

        RawIdentity(String name, String email, String login) {
            this.name = name;
            this.email = email;
            this.login = login;
        }
    }
    
    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "");
    }

    private static String normName(String name) {
        String x = stripAccents(norm(name));
        x = x.replaceAll("[^a-z0-9 ]+", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private static String emailLocalPart(String email) {
        String e = norm(email);
        int at = e.indexOf('@');
        if (at <= 0) return "";
        return e.substring(0, at);
    }

    private static String loginFromNoReply(String email) {
        String e = norm(email);
        if (!e.endsWith("@users.noreply.github.com")) return "";
        String lp = emailLocalPart(e);
        int plus = lp.indexOf('+');
        if (plus >= 0 && plus + 1 < lp.length()) return lp.substring(plus + 1);        
        return lp;
    }

    private static class DSU {
        int[] parent;
        int[] rank;

        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }

        int find(int x) {
            return parent[x] == x ? x : (parent[x] = find(parent[x]));
        }

        void union(int nodeA, int nodeB) {
            nodeA = find(nodeA);
            nodeB = find(nodeB);
            if (nodeA == nodeB) return;
            if (rank[nodeA] < rank[nodeB]) parent[nodeA] = nodeB;
            else if (rank[nodeA] > rank[nodeB]) parent[nodeB] = nodeA;
            else { parent[nodeB] = nodeA; rank[nodeA]++; }
        }
    }

    // Package-visible for unit testing of the representative-selection rules.
    static RawIdentity pickBetterRep(RawIdentity a, RawIdentity b) {
        boolean aLogin = a.login != null && !a.login.isBlank();
        boolean bLogin = b.login != null && !b.login.isBlank();
        if (aLogin && !bLogin) return a;
        if (bLogin && !aLogin) return b;

        String ae = norm(a.email), be = norm(b.email);
        boolean aOpen = ae.endsWith("@opendeusto.es");
        boolean bOpen = be.endsWith("@opendeusto.es");
        if (aOpen && !bOpen) return a;
        if (bOpen && !aOpen) return b;

        boolean aNoReply = ae.endsWith("@users.noreply.github.com");
        boolean bNoReply = be.endsWith("@users.noreply.github.com");
        if (!aNoReply && bNoReply) return a;
        if (!bNoReply && aNoReply) return b;

        int al = (a.name == null) ? 0 : a.name.length();
        int bl = (b.name == null) ? 0 : b.name.length();
        return (bl > al) ? b : a;
    }

    private Map<SimpleGitUser, List<GHCommit>> resolveAndMergeAuthors(Map<RawIdentity, List<GHCommit>> rawMap) {
        List<RawIdentity> ids = new ArrayList<>(rawMap.keySet());
        int[] root = clusterIdentities(ids);

        Map<Integer, List<GHCommit>> commitsByRoot = new HashMap<>();
        Map<Integer, RawIdentity> repByRoot = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) {
            int r = root[i];
            commitsByRoot.computeIfAbsent(r, k -> new ArrayList<>()).addAll(rawMap.get(ids.get(i)));
            repByRoot.merge(r, ids.get(i), GitHubDataLoader::pickBetterRep);
        }

        Map<SimpleGitUser, List<GHCommit>> out = new HashMap<>();
        for (Map.Entry<Integer, List<GHCommit>> e : commitsByRoot.entrySet()) {
            RawIdentity rep = repByRoot.get(e.getKey());
            String bestName = (rep.login != null && !rep.login.isBlank()) ? rep.login : rep.name;
            String bestEmail = rep.email;
            out.put(new SimpleGitUser(bestName, bestEmail), e.getValue());
        }

        return out;
    }

    /**
     * Clusters raw identities that (very likely) belong to the same person and
     * returns, for each identity, the index of its cluster representative root.
     * Two identities are merged when they share a GitHub login, share a
     * non-noreply email local-part, or share a normalized display name that is
     * anchored by at least one "strong" identity (has a login or a resolvable
     * email). This is the pure, side-effect-free core of
     * {@link #resolveAndMergeAuthors(Map)} and is package-visible for testing.
     *
     * @param ids the raw identities (order defines the returned indices)
     * @return an array {@code root} where {@code root[i]} is the cluster id of
     *         {@code ids.get(i)}; two identities share a cluster iff their roots
     *         are equal
     */
    static int[] clusterIdentities(List<RawIdentity> ids) {
        DSU dsu = new DSU(ids.size());

        Map<String, Integer> seenLogin = new HashMap<>();
        Map<String, Integer> seenEmailLocal = new HashMap<>();
        Map<String, List<Integer>> byNormName = new HashMap<>();

        for (int i = 0; i < ids.size(); i++) {
            RawIdentity id = ids.get(i);

            String login = norm(id.login);
            if (!login.isEmpty()) {
                seenLogin.merge("login:" + login, i, (oldI, newI) -> { dsu.union(oldI, newI); return oldI; });
            }

            String em = norm(id.email);
            String lp = emailLocalPart(em);
            if (!lp.isEmpty() && !em.endsWith("@users.noreply.github.com")) {
                seenEmailLocal.merge("lp:" + lp, i, (oldI, newI) -> { dsu.union(oldI, newI); return oldI; });
            }

            String nn = normName(id.name);
            if (!nn.isEmpty()) byNormName.computeIfAbsent(nn, k -> new ArrayList<>()).add(i);
        }

        for (List<Integer> idxs : byNormName.values()) {
            if (idxs.size() < 2) continue;

            Integer anchor = null;
            for (int idx : idxs) {
                RawIdentity id = ids.get(idx);
                boolean strong =
                        !norm(id.login).isEmpty()
                        || !loginFromNoReply(id.email).isEmpty()
                        || (!emailLocalPart(id.email).isEmpty() && !norm(id.email).endsWith("@users.noreply.github.com"));

                if (strong) { anchor = idx; break; }
            }
            if (anchor == null) continue;

            for (int idx : idxs) dsu.union(anchor, idx);
        }

        int[] root = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) root[i] = dsu.find(i);
        return root;
    }

    
    // ---------------- COMMITS ----------------

    private Map<RawIdentity, List<GHCommit>> collectCommitsAllBranches(GHRepository repository,
                                                                        Map<String, GHBranch> branches,
                                                                        RepoStats repoStats,
                                                                        StringBuilder buffer) {
    	Map<RawIdentity, List<GHCommit>> result = new HashMap<>();
        Set<String> processedCommits = new HashSet<>();

        long repoFirstCommitDate = Long.MAX_VALUE;
        long repoLastCommitDate = Long.MIN_VALUE;

        for (Map.Entry<String, GHBranch> branchEntry : branches.entrySet()) {
            GHBranch branch = branchEntry.getValue();
            try {
                PagedIterable<GHCommit> commits = repository.queryCommits().from(branch.getSHA1()).list();

                for (GHCommit c : commits) {
                    String sha = c.getSHA1();
                    if (sha == null || processedCommits.contains(sha)) continue;
                    processedCommits.add(sha);

                    try {
                        long t = c.getCommitDate().getTime();
                        repoFirstCommitDate = Math.min(repoFirstCommitDate, t);
                        repoLastCommitDate = Math.max(repoLastCommitDate, t);
                    } catch (Exception ignore) {}

                    RawIdentity author = resolveAuthorRaw(c);
                    result.computeIfAbsent(author, k -> new ArrayList<>()).add(c);
                }

            } catch (Exception ex) {
                buffer.append(String.format("\t* Error reading commits from branch '%s': %s\n",
                        branchEntry.getKey(), ex.getMessage()));
            }
        }

        repoStats.setCommits(processedCommits.size());
        repoStats.setFirstCommit(repoFirstCommitDate == Long.MAX_VALUE ? -1 : repoFirstCommitDate);
        repoStats.setLastCommit(repoLastCommitDate == Long.MIN_VALUE ? -1 : repoLastCommitDate);

        return result;
    }

    private RawIdentity resolveAuthorRaw(GHCommit c) {
        String name = "unknown";
        String email = "";
        String login = null;

        try {
            GHUser ghAuthor = c.getAuthor();
            if (ghAuthor != null) {
                login = ghAuthor.getLogin();
                if (login != null && !login.isBlank()) name = login;
                if (ghAuthor.getEmail() != null) email = ghAuthor.getEmail();
            }
        } catch (Exception ignore) {}

        try {
            GitUser authorInfo = c.getCommitShortInfo().getAuthor();
            if (authorInfo != null) {
                if ((name == null || "unknown".equals(name)) && authorInfo.getName() != null) name = authorInfo.getName();
                if ((email == null || email.isBlank()) && authorInfo.getEmail() != null) email = authorInfo.getEmail();
            }
        } catch (Exception ignore) {}

        if ((login == null || login.isBlank())) {
            String nr = loginFromNoReply(email);
            if (!nr.isBlank()) login = nr;
        }

        return new RawIdentity(name, email, login);
    }    

    private void fillUserStatsFromCommits(Map<SimpleGitUser, List<GHCommit>> commitsPerUser,
                                         RepoStats repoStats,
                                         StringBuilder buffer) {

        for (Map.Entry<SimpleGitUser, List<GHCommit>> entry : commitsPerUser.entrySet()) {
            SimpleGitUser user = entry.getKey();
            List<GHCommit> commits = entry.getValue();

            UserStats stats = computeUserStatsFromCommits(commits, user.getName(), user.getEmail(), buffer);
            repoStats.addUserStats(stats);

            buffer.append(String.format("\t* '%s' processed: rawCommits=%d, javaCommits=%d, churn=%d\n",
                    user.getName(),
                    commits.size(),
                    stats.getCommits(),
                    stats.getChanged()));
        }
    }

    private UserStats computeUserStatsFromCommits(List<GHCommit> commits,
                                                  String username,
                                                  String email,
                                                  StringBuilder buffer) {

        Set<String> javaFilesSet = new HashSet<>();
        int linesAdded = 0;
        int linesDeleted = 0;

        long firstCommitDate = Long.MAX_VALUE;
        long lastCommitDate = Long.MIN_VALUE;

        int commitsJava = 0;

        for (GHCommit commit : commits) {
            try {
                if (commit.getParents() != null && commit.getParents().size() > 1) {
                    continue;
                }

                long commitTime = -1;
                try { commitTime = commit.getCommitDate().getTime(); } catch (Exception ignore) {}
                if (commitTime != -1) {
                    firstCommitDate = Math.min(firstCommitDate, commitTime);
                    lastCommitDate = Math.max(lastCommitDate, commitTime);
                }

                List<GHCommit.File> files = safeListFiles(commit, buffer);

                boolean touchedJava = false;
                for (GHCommit.File file : files) {
                    String fileName = (file.getFileName() == null) ? "" : file.getFileName().toLowerCase();
                    if (fileName.endsWith(JAVA_EXTENSION)) {
                        touchedJava = true;
                        linesAdded += file.getLinesAdded();
                        linesDeleted += file.getLinesDeleted();
                        javaFilesSet.add(fileName);
                    }
                }

                if (touchedJava) commitsJava++;

            } catch (Exception ex) {
                buffer.append(String.format("\t  - Error processing commit for '%s': %s\n", username, ex.getMessage()));
            }
        }

        int churnJava = linesAdded + linesDeleted;

        long first = (firstCommitDate == Long.MAX_VALUE) ? -1 : firstCommitDate;
        long last = (lastCommitDate == Long.MIN_VALUE) ? -1 : lastCommitDate;

        return new UserStats(
                username,
                email,
                commitsJava,           
                javaFilesSet.size(),
                linesAdded,
                linesDeleted,
                churnJava,             
                first,
                last
        );
    }

    private List<GHCommit.File> safeListFiles(GHCommit commit, StringBuilder buffer) {
        boolean acquired = false;
        try {
            LIST_FILES_SEMAPHORE.acquire();
            acquired = true;

            throttleHeavyCalls();
            return commit.listFiles().toList();

        } catch (Exception ex) {
            buffer.append(String.format("\t  - Error listFiles() for commit %s: %s\n",
                    safeSha(commit), ex.getMessage()));
            return Collections.emptyList();
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
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String safeSha(GHCommit c) {
        try { return (c == null) ? "null" : c.getSHA1(); }
        catch (Exception e) { return "unknown"; }
    }
}