package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
import java.util.Collections;
import java.util.List;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;
import es.deusto.prog3.githubanalyzer.domain.UserStats;

public class DataManager {

    private static final DataManager instance = new DataManager();
    private final Object ioLock = new Object();  // lock interno para I/O

    private DataManager() { }

    public static DataManager getInstance() {
        return instance;
    }

    @SuppressWarnings("unchecked")
    public List<RepoStats> loadData() {
        String file = Configurator.getInstance().getStatsFile();
        Path p = Paths.get(file);

        if (!Files.exists(p)) {
            System.out.format("- No cache found at '%s' (starting empty)\n\n", file);
            return new ArrayList<>();
        }

        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(p)))) {
            List<RepoStats> data = (List<RepoStats>) in.readObject();
            if (data == null) data = new ArrayList<>();
            System.out.format("- %d RepoStats loaded from '%s'\n\n", data.size(), file);
            return data;
        } catch (Exception ex) {
            System.err.format("* Error reading binary file '%s': %s\n\n", file, ex.getMessage());
            return new ArrayList<>();
        }
    }

    /** Full save (thread-safe + atomic write). */
    public void storeData(List<RepoStats> data) {
        if (data == null) data = new ArrayList<>();

        synchronized (ioLock) {
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

                System.out.format("- %d RepoStats stored in '%s'\n\n", data.size(), file);
            } catch (Exception ex) {
                System.err.format("* Error saving binary file '%s': %s\n\n", file, ex.getMessage());
                // best-effort cleanup
                try { Files.deleteIfExists(tmp); } catch (Exception ignore) {}
            }
        }
    }

    /**
     * Incremental update: insert/update a single RepoStats and persist safely.
     * Call this after finishing analysis of each repository.
     */
    public void upsertRepoStats(RepoStats repoStats) {
        if (repoStats == null || repoStats.getUrl() == null) return;

        synchronized (ioLock) {
            List<RepoStats> data = loadData(); // load current cache

            boolean replaced = false;
            for (int i = 0; i < data.size(); i++) {
                RepoStats r = data.get(i);
                if (r != null && repoStats.getUrl().equals(r.getUrl())) {
                    data.set(i, repoStats);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) data.add(repoStats);

            // Keep cache ordered and consistent
            Collections.sort(data);
            data.forEach(r -> {
                if (r != null && r.getUserStats() != null) Collections.sort(r.getUserStats());
            });

            storeData(data); // atomic persist
        }
    }

    public void storeCSV(List<RepoStats> data) {
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

                // Repo-level Java churn used for computing the share
                final int repoChurn = repo.getLinesChanged();

                for (UserStats u : repo.getUserStats()) {
                    if (u == null) continue;

                    int churn = u.getAdded() + u.getDeleted();
                    float share = (repoChurn <= 0) ? 0f : (churn / (float) repoChurn);

                    out.format(java.util.Locale.ROOT,
                            "%s;%s;%s;%s;%d;%d;%d;%.6f;%d;%d;%s;%s%n",
                            "",                         // GROUP (kept for backward compatibility)
                            nullSafe(repo.getUrl()),
                            nullSafe(u.getUsername()),
                            nullSafe(u.getEmail()),
                            u.getAdded(),
                            u.getDeleted(),
                            churn,
                            share,                      // 0..1 fraction (GUI shows it as percentage)
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