/**
 * This code was developed with AI assistance (ChatGPT) and has been reviewed and validated for correctness.
 */
package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
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

public class DataManager {

    private static final DataManager instance = new DataManager();

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
        return loadData(Paths.get(Configurator.getInstance().getStatsFile()));
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
        Path target = Paths.get(file);
        Path tmp = Paths.get(file + ".tmp");

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
            System.err.format("* Error saving binary file '%s': %s\n\n", file, ex.getMessage());
            // best-effort cleanup
            try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
        }
    }

    public synchronized void storeCSV(List<RepoStats> data) {
        final String csvPath = Configurator.getInstance().getStatsCSV();

        try (PrintWriter out = new PrintWriter(
                new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(csvPath),
                        java.nio.charset.StandardCharsets.UTF_8))) {

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");

            // Header compatible with GUI metrics (plus repo URL for grouping)
            out.println("GROUP;URL;USERNAME;EMAIL;JAVA_ADDED;JAVA_DELETED;JAVA_CHURN;JAVA_CHURN_SHARE;JAVA_FILES;JAVA_COMMITS;LAST_COMMIT;FIRST_COMMIT");

            for (RepoStats repo : (data == null ? java.util.Collections.<RepoStats>emptyList() : data)) {
                if (repo == null || repo.getUserStats() == null) continue;

                for (UserStats u : repo.getUserStats()) {
                    if (u == null) continue;

                    int churn = u.getChurn();
                    // Share of the team churn (excluding teacher); same denominator as the GUI.
                    float share = ContributionMetrics.teamShare(repo, u);
                    String shareStr = Float.isNaN(share)
                            ? "-"                                                       // teacher row
                            : String.format(java.util.Locale.ROOT, "%.6f", share);      // 0..1 fraction

                    out.format(java.util.Locale.ROOT,
                            "%s;%s;%s;%s;%d;%d;%d;%s;%d;%d;%s;%s%n",
                            nullSafe(repo.getGroup()),
                            nullSafe(repo.getUrl()),
                            nullSafe(u.getUsername()),
                            nullSafe(u.getEmail()),
                            u.getAdded(),
                            u.getDeleted(),
                            churn,
                            shareStr,
                            u.getJavaFiles(),
                            u.getCommits(),
                            fmtDateOrDash(sdf, u.getLastCommit()),
                            fmtDateOrDash(sdf, u.getFirstCommit())
                    );
                }
            }

            System.out.format("- CSV stored in '%s'%n%n", csvPath);

        } catch (Exception ex) {
            System.err.format("* Error saving CSV file '%s': %s%n%n", csvPath, ex.getMessage());
        }
    }

    private static String nullSafe(String s) {
        return (s == null) ? "" : s;
    }

    private static String fmtDateOrDash(SimpleDateFormat sdf, long ts) {
        if (ts < 0) return "-";
        return sdf.format(new java.util.Date(ts));
    }
}