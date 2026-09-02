// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */
package es.deusto.prog3.githubanalyzer.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

/**
 * Tests for the team-relative contribution metrics. The share denominator is the
 * churn of the active contributors (excluding the teacher), so the shares of a
 * team must sum to 1.
 *
 * <p>Test users are deliberately named so they never match the configured
 * teacher account, which keeps these tests independent of the local
 * {@code config.properties}.
 */
public class ContributionMetricsTest {

    // UserStats(username, email, commits, javaFiles, added, deleted, changed, firstCommit, lastCommit)
    private static UserStats user(String name, int added, int deleted, int commits) {
        return new UserStats(name, name + "@student.example", commits, 1, added, deleted, added + deleted, 0L, 0L);
    }

    private static RepoStats repoWith(UserStats... users) {
        RepoStats r = new RepoStats();
        for (UserStats u : users) r.addUserStats(u);
        return r;
    }

    @Test
    public void isRealContributorRequiresChurnOrCommits() {
        assertTrue(ContributionMetrics.isRealContributor(user("alice", 40, 20, 3)));
        assertTrue(ContributionMetrics.isRealContributor(user("noChurnButCommits", 0, 0, 2)));
        assertFalse(ContributionMetrics.isRealContributor(user("ghost", 0, 0, 0)));
        assertFalse(ContributionMetrics.isRealContributor(null));
    }

    @Test
    public void activeContributorsExcludeNonContributors() {
        UserStats alice = user("alice", 40, 20, 3); // churn 60
        UserStats bob   = user("bob", 30, 10, 2);   // churn 40
        UserStats ghost = user("ghost", 0, 0, 0);   // no activity
        RepoStats repo = repoWith(alice, bob, ghost);

        List<UserStats> active = ContributionMetrics.activeContributors(repo);
        assertEquals(2, active.size());
        assertTrue(active.contains(alice));
        assertTrue(active.contains(bob));
        assertFalse(active.contains(ghost));
    }

    @Test
    public void teamChurnSumsActiveContributorsOnly() {
        RepoStats repo = repoWith(
                user("alice", 40, 20, 3),  // 60
                user("bob", 30, 10, 2),    // 40
                user("ghost", 0, 0, 0));   // excluded
        assertEquals(100, ContributionMetrics.teamChurn(repo));
    }

    @Test
    public void teamSharesSumToOne() {
        UserStats alice = user("alice", 40, 20, 3); // 60 -> 0.6
        UserStats bob   = user("bob", 30, 10, 2);   // 40 -> 0.4
        RepoStats repo = repoWith(alice, bob);

        float sAlice = ContributionMetrics.teamShare(repo, alice);
        float sBob = ContributionMetrics.teamShare(repo, bob);

        assertEquals(0.6f, sAlice, 1e-6);
        assertEquals(0.4f, sBob, 1e-6);
        assertEquals(1.0f, sAlice + sBob, 1e-6, "Team shares must sum to 1");
    }

    @Test
    public void teamShareIsZeroForNonContributorWhenTeamHasChurn() {
        UserStats alice = user("alice", 40, 20, 3);
        UserStats ghost = user("ghost", 0, 0, 0);
        RepoStats repo = repoWith(alice, ghost);

        assertEquals(0f, ContributionMetrics.teamShare(repo, ghost), 1e-6);
    }

    @Test
    public void teamShareIsZeroWhenNoTeamChurn() {
        // Everyone has commits but no churn -> denominator 0 -> share 0 (no division by zero).
        UserStats a = user("a", 0, 0, 1);
        UserStats b = user("b", 0, 0, 1);
        RepoStats repo = repoWith(a, b);

        assertEquals(0, ContributionMetrics.teamChurn(repo));
        assertEquals(0f, ContributionMetrics.teamShare(repo, a), 1e-6);
    }
}
