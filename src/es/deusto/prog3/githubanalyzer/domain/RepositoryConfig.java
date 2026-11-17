package es.deusto.prog3.githubanalyzer.domain;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One repository, an optional group, and an optional positive team size. */
public record RepositoryConfig(String url, String group, Integer teamSize) {
    public RepositoryConfig {
        if (teamSize != null && teamSize <= 0) throw new IllegalArgumentException("Team size must be positive.");
    }

    public static List<RepositoryConfig> parseLines(List<String> lines) {
        Map<String, RepositoryConfig> result = new LinkedHashMap<>();
        if (lines == null) return List.of();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank() || line.stripLeading().startsWith("#")) continue;
            try {
                RepositoryConfig next = parse(line);
                // GitHub repository identities ignore scheme and case. Preserve
                // the first configured spelling so existing raw data and author
                // assignments (keyed by URL) remain readable after upgrading.
                String key = URI.create(next.url()).getPath().toLowerCase(java.util.Locale.ROOT);
                RepositoryConfig previous = result.get(key);
                if (previous != null) {
                    if (!previous.group().isEmpty() && !next.group().isEmpty() && !previous.group().equals(next.group())
                            || previous.teamSize() != null && next.teamSize() != null && !previous.teamSize().equals(next.teamSize())) {
                        throw new IllegalArgumentException("Conflicting settings for duplicate repository.");
                    }
                    next = new RepositoryConfig(previous.url(), previous.group().isEmpty() ? next.group() : previous.group(),
                            previous.teamSize() == null ? next.teamSize() : previous.teamSize());
                }
                result.put(key, next);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Repositories line " + (i + 1) + ": " + ex.getMessage(), ex);
            }
        }
        return new ArrayList<>(result.values());
    }

    private static RepositoryConfig parse(String line) {
        boolean semicolon = line.contains(";");
        if (semicolon && line.contains(",")) throw new IllegalArgumentException("Do not mix comma and semicolon separators.");
        String[] fields = line.trim().split(semicolon ? ";" : ",", -1);
        if (fields.length > 3) throw new IllegalArgumentException("Expected URL[;GROUP][;TEAM_SIZE].");
        String url = fields[0].trim();
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid GitHub URL."); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || !"github.com".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?")) {
            throw new IllegalArgumentException("Expected a GitHub repository URL (https://github.com/OWNER/REPO).");
        }
        String path = uri.getPath().replaceAll("/+$", "").toLowerCase(java.util.Locale.ROOT).replaceAll("\\.git$", "");
        String[] segments = path.substring(1).split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw new IllegalArgumentException("Invalid GitHub repository path.");
        }
        url = url.replaceAll("/+$", "").replaceAll("(?i)\\.git$", "");
        String group = "";
        Integer size = null;
        if (fields.length == 3) {
            group = fields[1].trim();
            if (!fields[2].isBlank()) size = parseSize(fields[2].trim());
        } else if (fields.length == 2) {
            String last = fields[1].trim();
            if (last.matches("[+-]?[0-9]+(?:\\.[0-9]+)?")) size = parseSize(last);
            else group = last;
        }
        return new RepositoryConfig(url, group, size);
    }

    private static int parseSize(String value) {
        try {
            if (!value.matches("[0-9]+")) throw new NumberFormatException();
            int size = Integer.parseInt(value);
            if (size <= 0) throw new NumberFormatException();
            return size;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Team size must be a positive integer.");
        }
    }
}
