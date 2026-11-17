// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI ASSISTANCE AND REVIEW DISCLOSURE
 *
 * The initial version of this codebase was developed in 2024 with partial
 * assistance from ChatGPT (OpenAI).
 *
 * From July to September 2026, the codebase was reviewed and audited using
 * Claude Code (Anthropic) and Codex (OpenAI).
 *
 * The resulting version was reviewed, tested, and refined to identify and
 * correct issues within the scope of the performed verification activities.
 */

package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import es.deusto.prog3.githubanalyzer.format.NumberFormats;

public class DataManager {

    private static final DataManager instance = new DataManager();
    private static final char CSV_SEPARATOR = ';';
    private static final char UTF8_BOM = '\uFEFF';

    private DataManager() { }

    public static DataManager getInstance() {
        return instance;
    }

    /**
     * Restricts deserialization of the local cache to the classes we actually
     * write, with resource limits, to mitigate Java deserialization risks
     * (gadget-chain code execution and decompression/allocation DoS) if the
     * {@code .dat} file is tampered with or replaced (JEP 290).
     */
    private static final ObjectInputFilter CACHE_DESERIALIZATION_FILTER =
            ObjectInputFilter.Config.createFilter(
                    "maxbytes=52428800;maxdepth=32;maxrefs=2000000;maxarray=2000000;"
                    + "es.deusto.prog3.githubanalyzer.domain.*;"
                    + "java.util.*;java.lang.*;!*");

    public synchronized List<RepoStats> loadData() {
        String file = Configurator.getInstance().getStatsFile();
        if (file == null || file.isBlank()) {
            System.out.format("- No cache file configured (starting empty)\n");
            return new ArrayList<>();
        }
        return loadData(Paths.get(file));
    }

    /** Package-visible for testing: load and deserialize the cache from a specific path. */
    @SuppressWarnings("unchecked")
    synchronized List<RepoStats> loadData(Path p) {
        String file = p.toString();

        if (!Files.exists(p)) {
            System.out.format("- No cache found at '%s' (starting empty)\n", file);
            return new ArrayList<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            in.setObjectInputFilter(CACHE_DESERIALIZATION_FILTER);

            Object obj = in.readObject();
            if (obj == null) return new ArrayList<>();

            if (!(obj instanceof List<?>)) {
                System.err.format("* Unexpected cache content in '%s' (starting empty)\n\n", file);
                return new ArrayList<>();
            }

            // Defensive: make sure every element really is a RepoStats before we
            // hand the list to the rest of the app (avoids later ClassCastException).
            for (Object element : (List<?>) obj) {
                if (element != null && !(element instanceof RepoStats)) {
                    System.err.format("* Unexpected cache element in '%s' (starting empty)\n\n", file);
                    return new ArrayList<>();
                }
            }

            return (List<RepoStats>) obj;
        } catch (Exception ex) {
            System.err.format("* Error reading binary file '%s': %s\n\n", file, ex.getMessage());
            System.out.format("- No cache found at '%s' (starting empty)\n\n", file);
            return new ArrayList<>();
        }
    }

    /** Full save (thread-safe + atomic write). */
    public synchronized void storeData(List<RepoStats> data) {
        if (data == null) data = new ArrayList<>();

        String file = Configurator.getInstance().getStatsFile();
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("No cache file configured; data not saved.");
        }
        storeData(data, Paths.get(file));
    }

    /** Package-visible destination for atomic-save regression tests. */
    synchronized void storeData(List<RepoStats> data, Path target) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        try {
            // Write tmp
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmp,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)))) {
                out.writeObject(data);
            }

            // Atomic replace
            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (Exception ex) {
            // best-effort cleanup
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            throw new IllegalStateException("Could not save statistics to '" + target + "'.", ex);
        }
    }

    public synchronized void storeCSV(List<RepoStats> data) {
        final String csvPath = Configurator.getInstance().getStatsCSV();
        if (csvPath == null || csvPath.isBlank()) {
            throw new CsvExportException(csvPath, data, new IllegalStateException("No CSV destination configured."));
        }
        try {
            storeCSV(data, Paths.get(csvPath));
            System.out.format("- CSV stored in '%s'%n%n", csvPath);
        } catch (CsvExportException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CsvExportException(csvPath, data, ex);
        }
    }

    /** Replace the destination only after the complete CSV has been written and closed. */
    synchronized void storeCSV(List<RepoStats> data, Path csvPath) {
        Path temporary = null;
        try {
            Path target = csvPath.toAbsolutePath();
            temporary = Files.createTempFile(target.getParent(), ".csv-export-", ".tmp");
            writeCSV(data, temporary);
            // If atomic replacement is unavailable, fail without risking the previous export.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (java.io.IOException cleanup) { ex.addSuppressed(cleanup); }
            }
            throw new CsvExportException(csvPath == null ? null : csvPath.toString(), data, ex);
        }
    }

    private static void writeCSV(List<RepoStats> data, Path temporary) throws java.io.IOException {
        try (BufferedWriter out = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd", java.util.Locale.ROOT);

            // The BOM makes UTF-8 accents import correctly in common spreadsheet
            // applications. Text cells are quoted and formula-hardened below.
            out.write(UTF8_BOM);
            writeCsvRow(out, "GROUP", "URL", "USERNAME", "EMAIL", "JAVA_ADDED", "JAVA_DELETED",
                    "JAVA_CHURN", "JAVA_CHURN_SHARE", "JAVA_FILES", "JAVA_COMMITS", "LAST_COMMIT", "FIRST_COMMIT",
                    "TEAM_SIZE_DECLARED", "CONTRIBUTORS", "AUTHOR_IDENTITIES", "TEAM_SIZE_EFFECTIVE", "EXPECTED_SHARE", "PERSON_ID", "PARTICIPATION");

            for (RepoStats repo : (data == null ? java.util.Collections.<RepoStats>emptyList() : data)) {
                if (repo == null || repo.getUserStats() == null) continue;

                for (UserStats u : repo.getUserStats()) {
                    if (u == null) continue;

                    int churn = u.getChurn();
                    // Share of the team churn (excluding teacher); same denominator as the GUI.
                    float share = ContributionMetrics.teamShare(repo, u);
                    String shareStr = NumberFormats.csvShare(share); // numeric 0..1 fraction, decimal comma

                    writeCsvRow(out,
                            csvTextCell(repo.getGroup()),
                            csvTextCell(repo.getUrl()),
                            csvTextCell(u.getUsername()),
                            csvTextCell(u.getEmail()),
                            Integer.toString(u.getAdded()),
                            Integer.toString(u.getDeleted()),
                            Integer.toString(churn),
                            shareStr,
                            Integer.toString(u.getJavaFiles()),
                            Integer.toString(u.getCommits()),
                            fmtDateOrDash(sdf, u.getLastCommit()),
                            fmtDateOrDash(sdf, u.getFirstCommit()),
                            repo.getTeamSize() == null ? "" : repo.getTeamSize().toString(),
                            Integer.toString(ContributionMetrics.activeContributors(repo).size()),
                            Integer.toString(repo.getAuthorCount()),
                            Integer.toString(ContributionMetrics.effectiveTeamSize(repo)),
                            NumberFormats.csvShare(ContributionMetrics.expectedShare(repo)),
                            csvTextCell(u.getPersonId()),
                            csvTextCell(u.getParticipationRole() == null ? (ContributionMetrics.isTeacher(u) ? "TEACHER" : "MEMBER") : u.getParticipationRole().name()));
                }
            }

        }
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s;
    }

    /**
     * Returns one correctly quoted text cell for this semicolon-separated export.
     * A leading apostrophe makes formula-looking values literal in spreadsheet
     * applications instead of allowing CSV formula injection.
     */
    static String csvTextCell(String value) {
        String text = nullSafe(value);
        String leading = text.stripLeading();
        if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static void writeCsvRow(BufferedWriter out, String... cells) throws java.io.IOException {
        out.write(String.join(String.valueOf(CSV_SEPARATOR), cells));
        out.write("\r\n");
    }

    private static String fmtDateOrDash(SimpleDateFormat sdf, long ts) {
        if (ts < 0) return "-";
        return sdf.format(new java.util.Date(ts));
    }
}
