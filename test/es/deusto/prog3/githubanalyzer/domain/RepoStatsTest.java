package es.deusto.prog3.githubanalyzer.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class RepoStatsTest {

    @Test
    public void testAddUserStats() {
        RepoStats stats = new RepoStats();

        // Initial state
        assertTrue(stats.getUserStats().isEmpty());
        assertEquals(0, stats.getCommits());
        assertEquals(0, stats.getLinesAdded());
        assertEquals(0, stats.getLinesDeleted());
        assertEquals(0, stats.getLinesChanged());
        assertEquals(-1, stats.getFirstCommit());
        assertEquals(-1, stats.getLastCommit());

        // public UserStats(String username, String email, int commits, int javaFiles, int added, int deleted, int changed, long firstCommit, long lastCommit)
        UserStats u1 = new UserStats("Alice", "alice@test.com", 5, 2, 100, 20, 30, 1000L, 5000L);
        UserStats u2 = new UserStats("Bob", "bob@test.com", 3, 1, 50, 10, 5, 500L, 6000L);
        stats.addUserStats(u1);
        stats.addUserStats(u2);

        // addUserStats() only appends to the list; by design it does NOT
        // aggregate the repo-level counters (that is done explicitly by the
        // data loader). Adding a null must be a no-op.
        stats.addUserStats(null);

        assertEquals(2, stats.getUserStats().size());
        assertTrue(stats.getUserStats().contains(u1));
        assertTrue(stats.getUserStats().contains(u2));
        assertEquals(0, stats.getCommits(), "addUserStats must not touch repo-level counters");
        assertEquals(0, stats.getLinesAdded(), "addUserStats must not touch repo-level counters");
        assertEquals(0, stats.getLinesChanged(), "addUserStats must not touch repo-level counters");

        // Repo-level aggregates are populated explicitly, the same way
        // GitHubDataLoader does after all users are collected.
        stats.setLinesAdded(stats.getUserStats().stream().mapToInt(UserStats::getAdded).sum());
        stats.setLinesDeleted(stats.getUserStats().stream().mapToInt(UserStats::getDeleted).sum());
        stats.setLinesChanged(stats.getUserStats().stream().mapToInt(UserStats::getChanged).sum());

        assertEquals(150, stats.getLinesAdded());
        assertEquals(30, stats.getLinesDeleted());
        assertEquals(35, stats.getLinesChanged());
    }

    @Test
    public void testAddUserStatsDuplicate() {
        RepoStats stats = new RepoStats();
        UserStats u1 = new UserStats("Alice", "alice@test.com", 5, 2, 100, 20, 30, 1000L, 5000L);

        stats.addUserStats(u1);
        stats.addUserStats(u1); // Same instance: ignored by the contains() check

        assertEquals(1, stats.getUserStats().size(), "Should ignore duplicates");

        // A different instance that is equal() (username + email, case-insensitive)
        // must also be deduplicated.
        UserStats u1Equal = new UserStats("alice", "ALICE@TEST.COM", 9, 9, 9, 9, 9, 9L, 9L);
        stats.addUserStats(u1Equal);

        assertEquals(1, stats.getUserStats().size(), "Equal users (username+email) must be deduped");
    }

    @Test
    public void testCompareTo() {
        RepoStats r1 = new RepoStats();
        r1.setName("Alpha");
        
        RepoStats r2 = new RepoStats();
        r2.setName("Beta");
        
        assertTrue(r1.compareTo(r2) < 0, "Alpha should be before Beta");
        assertTrue(r2.compareTo(r1) > 0, "Beta should be after Alpha");
        assertEquals(0, r1.compareTo(r1), "Same object should compare to 0");
    }

    @Test
    public void testCompareToWithNullNames() {
        RepoStats r1 = new RepoStats(); // name is null
        RepoStats r2 = new RepoStats();
        r2.setName("Beta");
        
        // Before our fix, r1.compareTo(r2) threw NPE
        assertDoesNotThrow(() -> {
            assertTrue(r1.compareTo(r2) < 0, "Null name should be treated as empty and be before Beta");
        });
        
        RepoStats r3 = new RepoStats(); // name is null
        assertDoesNotThrow(() -> {
            assertEquals(0, r1.compareTo(r3), "Two nulls should be equivalent");
        });
    }
}
