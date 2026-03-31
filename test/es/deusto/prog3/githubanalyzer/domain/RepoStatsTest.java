package es.deusto.prog3.githubanalyzer.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class RepoStatsTest {

    @Test
    public void testAddUserStats() {
        RepoStats stats = new RepoStats();
        
        // Initial state
        assertEquals(0, stats.getCommits());
        assertEquals(0, stats.getLinesAdded());
        assertEquals(0, stats.getLinesDeleted());
        assertEquals(0, stats.getLinesChanged());
        assertEquals(-1, stats.getFirstCommit());
        assertEquals(-1, stats.getLastCommit());

        // public UserStats(String username, String email, int commits, int javaFiles, int added, int deleted, int changed, long firstCommit, long lastCommit)
        UserStats u1 = new UserStats("Alice", "alice@test.com", 5, 2, 100, 20, 30, 1000L, 5000L);
        stats.addUserStats(u1);
        
        assertEquals(1, stats.getUserStats().size());
        assertEquals(5, stats.getCommits());
        assertEquals(100, stats.getLinesAdded());
        assertEquals(20, stats.getLinesDeleted()); // Fix we implemented!
        assertEquals(30, stats.getLinesChanged()); // Fix we implemented!
        
        assertEquals(1000L, stats.getFirstCommit());
        assertEquals(5000L, stats.getLastCommit());
        
        UserStats u2 = new UserStats("Bob", "bob@test.com", 3, 1, 50, 10, 5, 500L, 6000L);
        stats.addUserStats(u2);
        
        assertEquals(2, stats.getUserStats().size());
        assertEquals(8, stats.getCommits());
        assertEquals(150, stats.getLinesAdded());
        assertEquals(30, stats.getLinesDeleted());
        assertEquals(35, stats.getLinesChanged());
        
        assertEquals(500L, stats.getFirstCommit(), "First commit should be min");
        assertEquals(6000L, stats.getLastCommit(), "Last commit should be max");
    }
    
    @Test
    public void testAddUserStatsDuplicate() {
        RepoStats stats = new RepoStats();
        UserStats u1 = new UserStats("Alice", "alice@test.com", 5, 2, 100, 20, 30, 1000L, 5000L);
        
        stats.addUserStats(u1);
        stats.addUserStats(u1); // Should not be added twice because of contains check
        
        assertEquals(1, stats.getUserStats().size(), "Should ignore duplicates");
        assertEquals(5, stats.getCommits(), "Stats should not be doubled");
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
