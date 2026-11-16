package es.deusto.prog3.githubanalyzer.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Original Git author metadata; similar names, emails or logins do not imply a merge. */
public record AuthorIdentity(String name, String email, String login) implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    public AuthorIdentity {
        name = clean(name);
        email = clean(email);
        login = clean(login);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }

    public String key() {
        // Length prefixes avoid collisions even if metadata contains delimiters.
        StringBuilder value = new StringBuilder();
        for (String field : new String[] {name, email, login}) {
            String normalized = field.toLowerCase(Locale.ROOT);
            value.append(normalized.length()).append(':').append(normalized);
        }
        return digest(value.toString());
    }

    public String displayName() {
        return !login.isEmpty() ? login : !name.isEmpty() ? name : !email.isEmpty() ? email : "Unknown author";
    }

    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
