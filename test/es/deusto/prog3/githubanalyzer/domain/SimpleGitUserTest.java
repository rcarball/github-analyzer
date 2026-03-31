package es.deusto.prog3.githubanalyzer.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class SimpleGitUserTest {

    @Test
    public void testEmailEquality() {
        SimpleGitUser user1 = new SimpleGitUser("Rober", "rober@gmail.com");
        SimpleGitUser user2 = new SimpleGitUser("Rober", "rober@yahoo.com");
        SimpleGitUser user3 = new SimpleGitUser("Rober", "rober@gmail.com");

        // The bug was that user1 and user2 were deemed equal because they shared "rober"
        assertNotEquals(user1, user2, "Users with different email domains should not be equal");
        assertEquals(user1, user3, "Users with same email should be equal");
    }

    @Test
    public void testNullInputs() {
        SimpleGitUser nullUser = new SimpleGitUser(null, null);
        assertEquals("", nullUser.getName(), "Name should be normalized to empty string");
        assertEquals("", nullUser.getEmail(), "Email should be normalized to empty string");
        
        SimpleGitUser otherNullUser = new SimpleGitUser(null, null);
        assertEquals(nullUser, otherNullUser, "Two users with null details should be equal to each other");
    }

    @Test
    public void testNormalization() {
        SimpleGitUser user1 = new SimpleGitUser("   Alice   ", "   ALICE@GMAIL.COM   ");
        assertEquals("alice", user1.getName());
        assertEquals("alice@gmail.com", user1.getEmail());
    }

    @Test
    public void testSameNameNoEmail() {
        SimpleGitUser u1 = new SimpleGitUser("Bob", null);
        SimpleGitUser u2 = new SimpleGitUser("Bob", "");
        assertEquals(u1, u2);
        
        SimpleGitUser u3 = new SimpleGitUser("Alice", null);
        assertNotEquals(u1, u3);
    }
}
