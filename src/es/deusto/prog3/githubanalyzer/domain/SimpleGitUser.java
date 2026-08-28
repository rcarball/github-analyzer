/**
 * This code was developed with AI assistance (ChatGPT and Claude) and reviewed by the author 
 * (see the unit tests for the validated parts).
 */

package es.deusto.prog3.githubanalyzer.domain;

import java.util.Objects;

public class SimpleGitUser {
    private final String name;
    private final String email;

    public SimpleGitUser(String name, String email) {
        this.name = norm(name);
        this.email = norm(email);
    }

    public String getName() { return name; }
    public String getEmail() { return email; }

    private static String norm(String s) {
        if (s == null) return "";
        s = s.trim();
        return s.isEmpty() ? "" : s.toLowerCase();
    }

    private String key() {
        if (!email.isEmpty()) return "e:" + email;
        if (!name.isEmpty()) return "n:" + name;
        return "unknown";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleGitUser other)) return false;
        return this.key().equals(other.key());
    }

    @Override
    public int hashCode() {
        return Objects.hash(key());
    }

    @Override
    public String toString() {
        return "SimpleGitUser[name=" + name + ", email=" + email + "]";
    }
}