package es.deusto.prog3.githubanalyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;

import es.deusto.prog3.githubanalyzer.domain.*;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Person;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot.Commit;

/** Deterministic local projection of raw data. No network or persistence dependencies. */
public final class StatisticsCalculator {
    private static final Pattern MARKERS = Pattern.compile("\\bIAG\\b|\\bFUENTE-EXTERNA\\b");
    private StatisticsCalculator() { }

    public static RepoStats calculate(RepositorySnapshot source, RepositoryConfig settings, AuthorshipConfig rules,
                                      Function<AuthorIdentity, Role> defaults) {
        if (!source.url().equals(settings.url())) throw new IllegalArgumentException("Raw data belongs to another repository.");
        RepoStats repo = new RepoStats();
        repo.setUrl(source.url()); repo.setName(source.name()); repo.setGroup(settings.group()); repo.setTeamSize(settings.teamSize());
        repo.setCreationDate(source.createdAt()); repo.setPublic(source.publicRepository()); repo.setLastPushTime(source.lastPushTime());
        repo.setDataCapturedAt(source.capturedAt()); repo.setBranches(source.branchHeads().size());
        List<Commit> active = source.activeCommits();
        repo.setCommits(active.size());
        repo.setMergeCommits((int) active.stream().filter(Commit::isMerge).count());
        repo.setFirstCommit(active.stream().mapToLong(Commit::timestamp).min().orElse(-1));
        repo.setLastCommit(active.stream().mapToLong(Commit::timestamp).max().orElse(-1));
        repo.setAuthorCount((int) active.stream().map(c -> c.author().key()).distinct().count());
        repo.setUserStats(personStats(active, rules, defaults));
        repo.setLinesAdded(repo.getUserStats().stream().mapToInt(UserStats::getAdded).sum());
        repo.setLinesDeleted(repo.getUserStats().stream().mapToInt(UserStats::getDeleted).sum());
        repo.setLinesChanged(repo.getLinesAdded() + repo.getLinesDeleted());
        Map<String, Integer> types = new TreeMap<>();
        int lines = 0, references = 0;
        for (var file : source.sourceFiles()) {
            String name = file.path().substring(file.path().lastIndexOf('/') + 1);
            int dot = name.lastIndexOf('.');
            if (dot < 0) continue;
            String extension = name.substring(dot).toLowerCase(Locale.ROOT);
            types.merge(extension, 1, Integer::sum);
            if (extension.equals(".java")) {
                lines += (int) file.javaContent().lines().count();
                references += file.javaContent().lines().mapToInt(StatisticsCalculator::countExternalMarkers).sum();
            }
        }
        repo.setCodeLines(lines); repo.setExternalReferences(references); repo.setFileTypeMap(types);
        repo.setFileSnapshotVersion(4); repo.setAnalysisComplete(true);
        return repo;
    }

    public static List<UserStats> personStats(List<Commit> commits, AuthorshipConfig rules, Function<AuthorIdentity, Role> defaults) {
        AuthorshipConfig config = rules.copy();
        Map<String, List<Commit>> grouped = new LinkedHashMap<>();
        Map<String, Person> people = new HashMap<>();
        Map<AuthorIdentity, String> keys = new HashMap<>();
        Map<String, List<Commit>> identities = new TreeMap<>();
        for (Commit commit : commits) {
            checkInterrupted();
            String key = keys.computeIfAbsent(commit.author(), AuthorIdentity::key);
            identities.computeIfAbsent(key, ignored -> new ArrayList<>()).add(commit);
        }
        for (List<Commit> values : identities.values()) {
            AuthorIdentity author = values.get(0).author();
            Person person = config.personFor(author);
            if (person == null) person = config.discover(author, defaults.apply(author));
            people.put(person.id(), person);
            grouped.computeIfAbsent(person.id(), key -> new ArrayList<>()).addAll(values);
        }
        List<UserStats> result = new ArrayList<>();
        grouped.forEach((id, values) -> {
            Person person = people.get(id);
            UserStats user = userStats(values, person.name(), person.emailFor(values.stream().map(Commit::author).distinct().toList()));
            user.setPerson(id, person.role()); result.add(user);
        });
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static UserStats userStats(List<Commit> commits, String name, String email) {
        var paths = new HashSet<String>();
        int added = 0, deleted = 0, javaCommits = 0;
        long first = Long.MAX_VALUE, last = Long.MIN_VALUE;
        for (Commit commit : commits) {
            checkInterrupted();
            if (commit.isMerge()) continue;
            if (commit.timestamp() != -1) { first = Math.min(first, commit.timestamp()); last = Math.max(last, commit.timestamp()); }
            boolean java = false;
            for (var file : commit.files()) if (file.isJava()) {
                java = true; added += file.added(); deleted += file.deleted(); paths.add(file.path());
            }
            if (java) javaCommits++;
        }
        return new UserStats(name, email, javaCommits, paths.size(), added, deleted, added + deleted,
                first == Long.MAX_VALUE ? -1 : first, last == Long.MIN_VALUE ? -1 : last);
    }

    public static List<AuthorSummary> authors(RepositorySnapshot source) {
        Map<String, List<Commit>> grouped = new LinkedHashMap<>();
        for (Commit commit : source.activeCommits()) grouped.computeIfAbsent(commit.author().key(), key -> new ArrayList<>()).add(commit);
        List<AuthorSummary> result = new ArrayList<>();
        grouped.values().forEach(commits -> {
            AuthorIdentity identity = commits.get(0).author();
            result.add(new AuthorSummary(identity, userStats(commits, "", "").getCommits()));
        });
        result.sort(Comparator.comparing((AuthorSummary summary) -> summary.identity().displayName()).thenComparing(a -> a.identity().key()));
        return result;
    }

    static int countExternalMarkers(String line) {
        if (line == null) return 0;
        return (int) MARKERS.matcher(line).results().count();
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Calculation cancelled.");
    }
}
