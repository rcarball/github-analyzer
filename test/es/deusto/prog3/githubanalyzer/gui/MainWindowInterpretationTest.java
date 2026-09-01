package es.deusto.prog3.githubanalyzer.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.gui.MainWindow.ContributionBadge;

/**
 * Unit tests for the pure contribution-interpretation logic extracted from
 * {@link MainWindow}: {@link MainWindow#classifyBadge(int, int, int, int)} and
 * {@link MainWindow#isAiPasteLike(int, int, int, int)}.
 *
 * <p>With 4 contributors, expected = 0.25, so the thresholds are:
 * veryLow = 0.125, okMin = 0.20, okMax = 0.30 (of the team churn share).
 */
public class MainWindowInterpretationTest {

    private static final int TEAM_CHURN = 1000;
    private static final int N = 4;               // expected share = 0.25
    private static final int COMMITS = 5;         // any positive number of Java commits

    private static ContributionBadge badge(int userChurn) {
        return MainWindow.classifyBadge(userChurn, COMMITS, TEAM_CHURN, N);
    }

    // ---------------- classifyBadge: guardrails ----------------

    @Test
    public void zeroChurnIsAlwaysVeryLow() {
        assertEquals(ContributionBadge.VERY_LOW, MainWindow.classifyBadge(0, COMMITS, TEAM_CHURN, N));
    }

    @Test
    public void zeroJavaCommitsIsAlwaysVeryLow() {
        assertEquals(ContributionBadge.VERY_LOW, MainWindow.classifyBadge(500, 0, TEAM_CHURN, N));
    }

    @Test
    public void zeroTeamChurnYieldsVeryLow() {
        // Division guardrail: teamChurn <= 0 => share = 0 => VERY_LOW.
        assertEquals(ContributionBadge.VERY_LOW, MainWindow.classifyBadge(50, COMMITS, 0, N));
    }

    // ---------------- classifyBadge: the four bands ----------------

    @Test
    public void shareBelowHalfExpectedIsVeryLow() {
        assertEquals(ContributionBadge.VERY_LOW, badge(100)); // 0.10 < 0.125
    }

    @Test
    public void shareBetweenVeryLowAndOkMinIsBelow() {
        assertEquals(ContributionBadge.BELOW, badge(150)); // 0.15 in [0.125, 0.20)
    }

    @Test
    public void shareWithinBalancedBandIsBalanced() {
        assertEquals(ContributionBadge.BALANCED, badge(250)); // 0.25 in [0.20, 0.30]
    }

    @Test
    public void shareAboveOkMaxIsHigh() {
        assertEquals(ContributionBadge.HIGH, badge(400)); // 0.40 > 0.30
    }

    // ---------------- classifyBadge: exact boundaries ----------------

    @Test
    public void veryLowBoundaryIsInclusiveOfBelow() {
        // share == 0.125 must NOT be VERY_LOW (strict <), it is the start of BELOW.
        assertEquals(ContributionBadge.BELOW, badge(125));
    }

    @Test
    public void okMinBoundaryIsBalanced() {
        assertEquals(ContributionBadge.BALANCED, badge(200)); // share == 0.20 -> BALANCED
    }

    @Test
    public void okMaxBoundaryIsBalanced() {
        assertEquals(ContributionBadge.BALANCED, badge(300)); // share == 0.30 -> BALANCED (inclusive)
    }

    @Test
    public void justAboveOkMaxIsHigh() {
        assertEquals(ContributionBadge.HIGH, badge(301)); // 0.301 > 0.30
    }

    @Test
    public void contributorCountZeroIsTreatedAsOne() {
        // n = max(1, 0) = 1 => expected = 1.0, okMax = 1.2; a full-share user is BALANCED.
        assertEquals(ContributionBadge.BALANCED, MainWindow.classifyBadge(1000, COMMITS, 1000, 0));
    }

    @Test
    public void shareIsRoundedToFourDecimals() {
        // 1/8 = 0.125 exactly -> BELOW boundary, confirming the rounding path.
        assertEquals(ContributionBadge.BELOW, MainWindow.classifyBadge(1, 2, 8, N));
    }

    // ---------------- isAiPasteLike ----------------

    @Test
    public void aiPasteTrueWhenChurnPerCommitFarAboveAverage() {
        // user: 500/2 = 250 per commit; team avg: 1000/50 = 20; 250 >= 20 * 2.5 (=50) -> true
        assertTrue(MainWindow.isAiPasteLike(500, 2, 1000, 50));
    }

    @Test
    public void aiPasteInclusiveAtExactThreshold() {
        // user: 100/2 = 50 per commit; avg 20; 50 >= 50 -> true
        assertTrue(MainWindow.isAiPasteLike(100, 2, 1000, 50));
    }

    @Test
    public void aiPasteFalseJustBelowThreshold() {
        // user: 90/2 = 45 per commit; threshold 50 -> false
        assertFalse(MainWindow.isAiPasteLike(90, 2, 1000, 50));
    }

    @Test
    public void aiPasteFalseWithoutCommits() {
        assertFalse(MainWindow.isAiPasteLike(500, 0, 1000, 50));
    }

    @Test
    public void aiPasteFalseWhenTeamAverageIsZero() {
        assertFalse(MainWindow.isAiPasteLike(500, 2, 1000, 0));
    }
}
