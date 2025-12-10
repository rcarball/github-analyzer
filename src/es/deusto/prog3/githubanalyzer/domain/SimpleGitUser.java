/**
 * This code is based on solutions provided by ChatGPT 5.1 and.
 * It has been thoroughly reviewed and validated to ensure correctness.
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

    private static String localPart(String email) {
        if (email == null) return "";
        email = norm(email);
        int at = email.indexOf('@');
        if (at <= 0) return "";
        return email.substring(0, at).trim();
    }

    private String key() {
        String lp = localPart(email);
        if (!lp.isEmpty()) return "lp:" + lp;
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