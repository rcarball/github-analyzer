/**
 * This code was developed with AI assistance (ChatGPT and Claude) and reviewed by the author 
 * (see the unit tests for the validated parts).
 */

package es.deusto.prog3.githubanalyzer.persistence;

import java.util.List;
import java.util.stream.Collectors;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

/**
 * Single source of truth for contribution-share metrics, shared by the GUI and
 * the CSV export so both always agree.
 *
 * <p>All "share" figures are relative to the <b>team churn</b>: the total Java
 * churn produced by the active contributors of a repository, <i>excluding the
 * teacher account</i>. This matches the {@code expected = 1/n} baseline used for
 * the contribution badges (n = number of active contributors excluding the
 * teacher), so a user's share and the expected share are directly comparable and
 * the shares of the team sum to 1.
 */
public final class ContributionMetrics {

    private ContributionMetrics() { }

    /** True if the user matches the configured teacher account (by username, full email, or email local-part). */
    public static boolean isTeacher(UserStats u) {
        if (u == null) return false;

        String tUser = Configurator.getInstance().getTeacherUser();
        String tEmail = Configurator.getInstance().getTeacherEmail();

        String user = (u.getUsername() == null) ? "" : u.getUsername().trim().toLowerCase();
        String email = (u.getEmail() == null) ? "" : u.getEmail().trim().toLowerCase();

        if (tUser != null && !tUser.isBlank() && user.equals(tUser.trim().toLowerCase())) return true;

        if (tEmail != null && !tEmail.isBlank()) {
            String te = tEmail.trim().toLowerCase();
            if (!email.isBlank() && email.equals(te)) return true;

            int at1 = email.indexOf('@');
            int at2 = te.indexOf('@');
            String lp1 = at1 > 0 ? email.substring(0, at1) : email;
            String lp2 = at2 > 0 ? te.substring(0, at2) : te;
            if (!lp1.isBlank() && lp1.equals(lp2)) return true;
        }
        return false;
    }

    /** A user counts as a real contributor when they have any Java churn or any Java commit. */
    public static boolean isRealContributor(UserStats u) {
        return u != null && (u.getChurn() > 0 || u.getCommits() > 0);
    }

    /** Active contributors of the repo: real contributors, excluding the teacher account. */
    public static List<UserStats> activeContributors(RepoStats repo) {
        if (repo == null || repo.getUserStats() == null) return List.of();
        return repo.getUserStats().stream()
                .filter(u -> !isTeacher(u))
                .filter(ContributionMetrics::isRealContributor)
                .collect(Collectors.toList());
    }

    /** Total Java churn produced by the active contributors (excluding the teacher). */
    public static int teamChurn(RepoStats repo) {
        return activeContributors(repo).stream().mapToInt(UserStats::getChurn).sum();
    }

    /**
     * The user's share of the team churn, in [0, 1].
     *
     * @return {@code Float.NaN} for the teacher account (excluded from the share
     *         model), {@code 0f} when the team produced no churn, otherwise the
     *         user's fraction of the team churn.
     */
    public static float teamShare(RepoStats repo, UserStats u) {
        if (isTeacher(u)) return Float.NaN;
        int denom = teamChurn(repo);
        return (denom <= 0) ? 0f : (float) u.getChurn() / denom;
    }
}
