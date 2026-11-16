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
    @Test
    public void declaredSizeChangesExpectedShareButNeverObservedShare() {
        UserStats a = user("first", 50, 0, 2), b = user("second", 50, 0, 2);
        RepoStats repo = repoWith(a, b);
        assertEquals(2, ContributionMetrics.effectiveTeamSize(repo));
        assertEquals(0.5f, ContributionMetrics.expectedShare(repo));
        repo.setTeamSize(4);
        assertEquals(4, ContributionMetrics.effectiveTeamSize(repo));
        assertEquals(0.25f, ContributionMetrics.expectedShare(repo));
        assertEquals(0.5f, ContributionMetrics.teamShare(repo, a));
        assertEquals(1f, ContributionMetrics.teamShare(repo, a) + ContributionMetrics.teamShare(repo, b));
        repo.setTeamSize(1);
        assertEquals(1, ContributionMetrics.effectiveTeamSize(repo), "A declared/observed mismatch is visible, never silently clamped");
    }

    @Test
    public void explicitRolesRemainAuthoritativeAfterRenameAndExcludeAllAliasesWork() {
        UserStats member = user("member", 50, 0, 1), teacher = user("renamed", 200, 0, 3), excluded = user("bot", 1000, 0, 5);
        member.setPerson("member", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        teacher.setPerson("teacher", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.TEACHER);
        excluded.setPerson("bot", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.EXCLUDED);
        RepoStats repo = repoWith(member, teacher, excluded);
        assertEquals(List.of(member), ContributionMetrics.activeContributors(repo));
        assertEquals(50, ContributionMetrics.teamChurn(repo));
        assertEquals(1f, ContributionMetrics.teamShare(repo, member));
        assertTrue(Float.isNaN(ContributionMetrics.teamShare(repo, teacher)));
        assertTrue(Float.isNaN(ContributionMetrics.teamShare(repo, excluded)));
        assertTrue(ContributionMetrics.isTeacher(teacher));
        assertFalse(ContributionMetrics.isTeacher(excluded));
    }

    @Test
    public void emptyTeamHasNoInferredExpectedShare() {
        assertTrue(Float.isNaN(ContributionMetrics.expectedShare(new RepoStats())));
        assertEquals(0, ContributionMetrics.effectiveTeamSize(null));
        RepoStats declared = new RepoStats(); declared.setTeamSize(4);
        assertEquals(0.25f, ContributionMetrics.expectedShare(declared));
    }
    @Test
    public void aLinkedStudentAccountIsNotExcludedBecauseItsGitNameMatchesTeacherUser(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        Configurator config = new Configurator(dir);
        config.save("test-user", "test-token", "shared-name", "teacher@faculty.example");
        var identity = new es.deusto.prog3.githubanalyzer.domain.AuthorIdentity("shared-name", "student@students.example", "student-account");
        var role = ContributionMetrics.defaultRole(identity, config);
        assertEquals(es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER, role);
        UserStats student = user("student-account", 50, 0, 1);
        student.setPerson("student-id", role);
        UserStats other = user("another-student", 50, 0, 1);
        other.setPerson("other-id", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        RepoStats repo = repoWith(student, other);
        assertEquals(2, ContributionMetrics.activeContributors(repo).size());
        assertEquals(0.5f, ContributionMetrics.expectedShare(repo));
        assertEquals(0.5f, ContributionMetrics.teamShare(repo, student));
    }

    @Test
    public void teacherDefaultsRetainLoginEmailAndUnlinkedNameMatching(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        Configurator config = new Configurator(dir);
        config.save("test-user", "test-token", "teacher-login", "teacher@faculty.example");
        var teacher = es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.TEACHER;
        assertEquals(teacher, ContributionMetrics.defaultRole(new es.deusto.prog3.githubanalyzer.domain.AuthorIdentity("Other name", "other@example.org", "teacher-login"), config));
        assertEquals(teacher, ContributionMetrics.defaultRole(new es.deusto.prog3.githubanalyzer.domain.AuthorIdentity("Other name", "teacher@faculty.example", "other-login"), config));
        assertEquals(teacher, ContributionMetrics.defaultRole(new es.deusto.prog3.githubanalyzer.domain.AuthorIdentity("teacher-login", "other@example.org", ""), config));
        UserStats explicitMember = user("teacher-login", 5, 0, 1);
        explicitMember.setPerson("explicit-member", es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role.MEMBER);
        assertFalse(ContributionMetrics.isTeacher(explicitMember, config), "Explicit per-repository roles still take precedence");
    }
}
