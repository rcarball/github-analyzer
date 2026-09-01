package es.deusto.prog3.githubanalyzer.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class UserStatsTest {

    @Test
    public void testEqualsAndHashCodeNormal() {
        UserStats u1 = new UserStats("Alice", "alice@test.com", 1, 1, 1, 1, 1, 0, 0);
        UserStats u2 = new UserStats("alice", "ALICE@TEST.COM", 1, 1, 1, 1, 1, 0, 0);
        
        assertEquals(u1, u2);
        assertEquals(u1.hashCode(), u2.hashCode());
    }

    @Test
    public void testEqualsAndHashCodeNullSafety() {
        UserStats u1 = new UserStats(null, null, 1, 1, 1, 1, 1, 0, 0);
        UserStats u2 = new UserStats(null, null, 1, 1, 1, 1, 1, 0, 0);
        
        // Before the fix, this would throw NullPointerException
        assertDoesNotThrow(() -> {
            boolean equals = u1.equals(u2);
            assertTrue(equals, "Two objects with null fields should be equal");
            
            assertEquals(u1.hashCode(), u2.hashCode(), "HashCodes should match for null fields");
        });
    }

    @Test
    public void testEqualsWithPartialNulls() {
        UserStats u1 = new UserStats("Alice", null, 1, 1, 1, 1, 1, 0, 0);
        UserStats u2 = new UserStats("Alice", null, 1, 1, 1, 1, 1, 0, 0);
        UserStats u3 = new UserStats(null, "alice@test.com", 1, 1, 1, 1, 1, 0, 0);
        UserStats u4 = new UserStats(null, "alice@test.com", 1, 1, 1, 1, 1, 0, 0);
        
        assertEquals(u1, u2);
        assertEquals(u3, u4);
        assertNotEquals(u1, u3);
    }
    
    @Test
    public void testDataRetrieval() {
        UserStats u1 = new UserStats("Bob", "bob@test.com", 10, 5, 100, 20, 30, 1000L, 2000L);
        assertEquals("Bob", u1.getUsername());
        assertEquals("bob@test.com", u1.getEmail());
        assertEquals(10, u1.getCommits());
        assertEquals(5, u1.getJavaFiles());
        assertEquals(100, u1.getAdded());
        assertEquals(20, u1.getDeleted());
        assertEquals(30, u1.getChanged());
        assertEquals(1000L, u1.getFirstCommit());
        assertEquals(2000L, u1.getLastCommit());
        
        assertEquals(120, u1.getChurn(), "Churn is added + deleted");
        assertEquals(80, u1.getNetLines(), "Net lines is added - deleted");
    }
}
