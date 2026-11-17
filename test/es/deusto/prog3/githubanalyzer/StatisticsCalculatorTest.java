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

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import es.deusto.prog3.githubanalyzer.domain.*;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.*;

class StatisticsCalculatorTest {
    private static final String URL = "https://github.com/course/project";
    private static final AuthorIdentity A = new AuthorIdentity("Name", "school@example.org", "first");
    private static final AuthorIdentity B = new AuthorIdentity("Alias", "home@example.org", "second");
    @Test void sourceScanMatchesLegacyLinesAndUnicodeWordBoundaries() {
        List<String> inputs = new ArrayList<>(List.of("", "\n", "\r", "\r\n", "a\r\nb\rc\nd\n", "IAG", "FUENTE-EXTERNA\r\n",
                "IAGIAG _IAG IAG_ 1IAG IAG1 IAG-FUENTE-EXTERNA", "éIAG IAG漢 \u0301IAG IAG\u0301 😀IAG😀 𐐀IAG IAG𐐀",
                "IAG\u0085FUENTE-EXTERNA\u2028IAG\u2029", "FUENTE-\nEXTERNA", "IAG\n\nFUENTE-EXTERNA"));
        String[] tokens = {"IAG", "FUENTE-EXTERNA", "iag", "x", "_", "7", "é", "漢", "\u0301", "𐐀", "😀", "\r", "\n", " ", "-", "\u2028", "\u0085", "\ud800", "\udc00"};
        Random random = new Random(5317);
        for (int sample = 0; sample < 1500; sample++) {
            StringBuilder text = new StringBuilder();
            for (int i = 0, count = random.nextInt(50); i < count; i++) text.append(tokens[random.nextInt(tokens.length)]);
            inputs.add(text.toString());
        }
        var legacy = java.util.regex.Pattern.compile("\\bIAG\\b|\\bFUENTE-EXTERNA\\b");
        for (String input : inputs) {
            var actual = StatisticsCalculator.sourceMetrics(input);
            assertEquals(input.lines().count(), actual.lines(), "Line boundaries changed: " + input);
            assertEquals(input.lines().mapToLong(line -> legacy.matcher(line).results().count()).sum(), actual.references(),
                    "Marker boundaries changed: " + input);
        }
    }

    @Test void largeMultiLineAndSingleLineSourcesKeepExactMetricsAndOriginalText() {
        String multi = "// IAG FUENTE-EXTERNA\r\nclass Big {}\n".repeat(100_000);
        String single = "x".repeat(2 * 1024 * 1024) + " IAG";
        var raw = new RepositorySnapshot(1, URL, "project", 1, 1, 1, true, "main", Map.of(), List.of(),
                List.of(new RepositorySnapshot.SourceFile("src/Large.JAVA", multi),
                        new RepositorySnapshot.SourceFile("src/OneLine.java", single), new RepositorySnapshot.SourceFile("README.md", null)));
        RepoStats stats = StatisticsCalculator.calculate(raw, new RepositoryConfig(URL, "", null), new AuthorshipConfig(), id -> Role.MEMBER);
        assertEquals(200_001, stats.getCodeLines()); assertEquals(200_001, stats.getExternalReferences());
        assertEquals(Map.of(".java", 2, ".md", 1), stats.getFileTypeMap());
        assertSame(multi, raw.sourceFiles().get(0).javaContent()); assertSame(single, raw.sourceFiles().get(1).javaContent());
    }

    @Test void sourceCalculationHonorsCancellationEvenWithoutCommitsOrLineBreaks() {
        for (String text : List.of("", "x".repeat(2 * 1024 * 1024))) {
            var raw = new RepositorySnapshot(1, URL, "project", 1, 1, 1, true, "main", Map.of(), List.of(),
                    List.of(new RepositorySnapshot.SourceFile("Large.java", text)));
            try {
                Thread.currentThread().interrupt();
                assertThrows(java.util.concurrent.CancellationException.class,
                        () -> StatisticsCalculator.calculate(raw, new RepositoryConfig(URL, "", null), new AuthorshipConfig(), id -> Role.MEMBER));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
        }
    }

    @Test void authorActivitySurvivesRebaseDatesWhileRepositoryTracksCommitterDates() {
        var first = new RepositorySnapshot.Commit("a", A, 5000, List.of(), List.of(), 1000L);
        var next = new RepositorySnapshot.Commit("b", A, 4000, List.of("a"), List.of(), 2000L);
        var merge = new RepositorySnapshot.Commit("merge", A, 6000, List.of("a", "b"), List.of(), 9000L);
        var source = new RepositorySnapshot(1, URL, "project", 7000, 1, 6000, true, "main", Map.of("main", "merge"),
                List.of(first, next, merge), List.of());
        RepoStats stats = StatisticsCalculator.calculate(source, new RepositoryConfig(URL, "G", 3), new AuthorshipConfig(), id -> Role.MEMBER);
        assertEquals(4000, stats.getFirstCommit()); assertEquals(6000, stats.getLastCommit());
        assertEquals(1000, stats.getUserStats().get(0).getFirstCommit());
        assertEquals(2000, stats.getUserStats().get(0).getLastCommit(), "A merge author date is excluded from individual activity");
    }

    @Test void legacyAndUnknownDatesHaveConsistentFallbacks() {
        var legacy = new RepositorySnapshot.Commit("a", A, 1234, List.of(), List.of());
        var unknown = new RepositorySnapshot.Commit("b", A, -1, List.of("a"), List.of());
        var source = new RepositorySnapshot(1, URL, "project", 5000, 1, 3000, true, "main", Map.of("main", "b"),
                List.of(unknown, legacy), List.of());
        RepoStats stats = StatisticsCalculator.calculate(source, new RepositoryConfig(URL, "", null), new AuthorshipConfig(), id -> Role.MEMBER);
        assertEquals(1234, stats.getFirstCommit()); assertEquals(1234, stats.getLastCommit());
        assertEquals(1234, stats.getUserStats().get(0).getFirstCommit()); assertEquals(1234, stats.getUserStats().get(0).getLastCommit());
        assertEquals(-1, StatisticsCalculator.userStats(List.of(unknown), "name", "mail").getFirstCommit());
        assertEquals(-1, StatisticsCalculator.userStats(List.of(unknown), "name", "mail").getLastCommit());
    }
    static RepositorySnapshot source() {
        var a = new RepositorySnapshot.Commit("a", A, 1000, List.of(), List.of(new RepositorySnapshot.ChangedFile("src/Test.java", "", 10, 2)));
        var b = new RepositorySnapshot.Commit("b", B, 2000, List.of("a"), List.of(new RepositorySnapshot.ChangedFile("src/Test.java", "", 20, 3)));
        var docs = new RepositorySnapshot.Commit("docs", A, 2500, List.of("b"), List.of(new RepositorySnapshot.ChangedFile("README.md", "", 999, 0)));
        var merge = new RepositorySnapshot.Commit("merge", A, 3000, List.of("b", "docs"), List.of(new RepositorySnapshot.ChangedFile("src/Test.java", "", 999, 0)));
        return new RepositorySnapshot(1, URL, "project", 4000, 1, 3000, true, "main", Map.of("main", "merge"),
                List.of(a, b, docs, merge), List.of(new RepositorySnapshot.SourceFile("src/Test.java", "// IAG\nclass Test {}\n")));
    }
    @Test void preferredEmailAndVisibleNameAffectOnlyPresentationAndFallBackAfterSplitting() {
        RepositorySnapshot raw = source(); AuthorshipConfig rules = new AuthorshipConfig();
        rules.putPerson(new Person("merged", "Visible name", Role.MEMBER, B.email(), B.login()));
        rules.assign(A.key(), "merged"); rules.assign(B.key(), "merged");
        var settings = new RepositoryConfig(URL, "G", null);
        RepoStats merged = StatisticsCalculator.calculate(raw, settings, rules, identity -> Role.MEMBER);
        UserStats user = merged.getUserStats().get(0);
        assertEquals("Visible name", user.getUsername()); assertEquals(B.email(), user.getEmail());
        assertEquals(35, user.getChurn()); assertEquals(2, user.getCommits());
        assertEquals(1f, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.teamShare(merged, user));
        rules.putPerson(new Person("split", "Other name", Role.MEMBER)); rules.assign(B.key(), "split");
        RepoStats split = StatisticsCalculator.calculate(raw, settings, rules, identity -> Role.MEMBER);
        assertEquals(A.email(), split.getUserStats().stream().filter(u -> u.getPersonId().equals("merged")).findFirst().orElseThrow().getEmail());
        assertEquals(source(), raw);
    }
    @Test void localMergeIsReversibleAndDoesNotMutateOriginalsOrRules() {
        RepositorySnapshot raw = source(); AuthorshipConfig separate = new AuthorshipConfig();
        RepositoryConfig settings = new RepositoryConfig(URL, "G01", 4);
        RepoStats before = StatisticsCalculator.calculate(raw, settings, separate, identity -> Role.MEMBER);
        AuthorshipConfig merged = separate.copy(); merged.putPerson(new Person("person", "One person", Role.MEMBER));
        merged.assign(A.key(), "person"); merged.assign(B.key(), "person");
        RepoStats after = StatisticsCalculator.calculate(raw, settings, merged, identity -> Role.MEMBER);
        assertEquals(2, before.getUserStats().size()); assertEquals(1, after.getUserStats().size());
        assertEquals(35, after.getUserStats().get(0).getChurn()); assertEquals(1, after.getUserStats().get(0).getJavaFiles());
        assertEquals(2, after.getUserStats().get(0).getCommits()); assertEquals(2500, after.getUserStats().get(0).getLastCommit());
        assertEquals(4, after.getCommits()); assertEquals(1, after.getMergeCommits()); assertEquals(2, after.getCodeLines()); assertEquals(1, after.getExternalReferences());
        assertEquals(2, StatisticsCalculator.calculate(raw, settings, new AuthorshipConfig(), identity -> Role.MEMBER).getUserStats().size());
        assertEquals(source(), raw); assertTrue(separate.assignments().isEmpty());
    }
    @Test void declaredSizeChangesOnlyExpectedShareAndExclusionsChangeObservedDenominator() {
        RepositorySnapshot raw = source(); AuthorshipConfig rules = new AuthorshipConfig();
        RepoStats two = StatisticsCalculator.calculate(raw, new RepositoryConfig(URL, "G", null), rules, identity -> Role.MEMBER);
        RepoStats four = StatisticsCalculator.calculate(raw, new RepositoryConfig(URL, "G", 4), rules, identity -> Role.MEMBER);
        assertEquals(es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.teamShare(two, two.getUserStats().get(0)),
                es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.teamShare(four, four.getUserStats().get(0)));
        assertEquals(.25f, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.expectedShare(four));
        rules.putPerson(new Person("excluded", "Other", Role.EXCLUDED)); rules.assign(B.key(), "excluded");
        RepoStats excluded = StatisticsCalculator.calculate(raw, new RepositoryConfig(URL, "G", null), rules, identity -> Role.MEMBER);
        assertEquals(1, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.activeContributors(excluded).size());
        assertEquals(12, es.deusto.prog3.githubanalyzer.persistence.ContributionMetrics.teamChurn(excluded));
    }
}
