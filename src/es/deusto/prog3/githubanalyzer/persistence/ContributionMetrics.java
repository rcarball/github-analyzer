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

package es.deusto.prog3.githubanalyzer.persistence;

import java.util.List;
import java.util.stream.Collectors;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.AuthorshipConfig.Role;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

/** Shared contribution definitions for the GUI, editor and CSV export.
 * Observed shares use actual Java work, excluding teacher and other excluded roles.
 * Expected shares use the declared team size, or the resolved active contributors.
 */
public final class ContributionMetrics {

    private ContributionMetrics() { }

    /** True if the user matches the configured teacher account (by username or full email). */
    public static boolean isTeacher(UserStats u) {
        return isTeacher(u, Configurator.getInstance());
    }

    static boolean isTeacher(UserStats u, Configurator config) {
        if (u == null) return false;
        if (u.getParticipationRole() != null) return u.getParticipationRole() == Role.TEACHER;

        String tUser = config.getTeacherUser();
        String tEmail = config.getTeacherEmail();

        String user = (u.getUsername() == null) ? "" : u.getUsername().trim().toLowerCase(java.util.Locale.ROOT);
        String email = (u.getEmail() == null) ? "" : u.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        if (tUser != null && !tUser.isBlank() && user.equals(tUser.trim().toLowerCase(java.util.Locale.ROOT))) return true;

        if (tEmail != null && !tEmail.isBlank()) {
            String te = tEmail.trim().toLowerCase(java.util.Locale.ROOT);
            if (!email.isBlank() && email.equals(te)) return true;

        }
        return false;
    }

    public static boolean isExcluded(UserStats user) {
        return user != null && (user.getParticipationRole() == Role.EXCLUDED || isTeacher(user));
    }

    /** Existing teacher settings initialize roles; explicit per-repository choices take precedence. */
    public static Role defaultRole(AuthorIdentity identity) {
        return defaultRole(identity, Configurator.getInstance());
    }

    static Role defaultRole(AuthorIdentity identity, Configurator config) {
        // A linked GitHub account takes precedence over the freely chosen Git name.
        UserStats author = new UserStats(identity.displayName(), identity.email(), 0, 0, 0, 0, 0, -1, -1);
        return isTeacher(author, config) ? Role.TEACHER : Role.MEMBER;
    }

    public static int effectiveTeamSize(RepoStats repo) {
        if (repo == null) return 0;
        return repo.getTeamSize() != null ? repo.getTeamSize() : activeContributors(repo).size();
    }

    public static float expectedShare(RepoStats repo) {
        int size = effectiveTeamSize(repo);
        return size > 0 ? 1f / size : Float.NaN;
    }

    /** A user counts as a real contributor when they have any Java churn or any Java commit. */
    public static boolean isRealContributor(UserStats u) {
        return u != null && (u.getChurn() > 0 || u.getCommits() > 0);
    }

    /** Active contributors of the repo: real contributors, excluding teachers and other excluded accounts. */
    public static List<UserStats> activeContributors(RepoStats repo) {
        if (repo == null || repo.getUserStats() == null) return List.of();
        return repo.getUserStats().stream()
                .filter(u -> !isExcluded(u))
                .filter(ContributionMetrics::isRealContributor)
                .collect(Collectors.toList());
    }

    /** Total Java churn produced by the active contributors (excluding teachers and other excluded accounts). */
    public static int teamChurn(RepoStats repo) {
        return activeContributors(repo).stream().mapToInt(UserStats::getChurn).sum();
    }

    /**
     * The user's share of the team churn, in [0, 1].
     *
     * @return {@code Float.NaN} for an excluded account (outside the share
     *         model), {@code 0f} when the team produced no churn, otherwise the
     *         user's fraction of the team churn.
     */
    public static float teamShare(RepoStats repo, UserStats u) {
        if (isExcluded(u)) return Float.NaN;
        int denom = teamChurn(repo);
        return (denom <= 0) ? 0f : (float) u.getChurn() / denom;
    }
}
