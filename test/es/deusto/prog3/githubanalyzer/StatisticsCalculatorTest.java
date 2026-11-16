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
