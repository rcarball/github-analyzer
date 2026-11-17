package es.deusto.prog3.githubanalyzer.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/** Export failed after calculation; keep the calculated results available to the UI. */
public final class CsvExportException extends IllegalStateException {
    private static final long serialVersionUID = 1L;
    private final List<RepoStats> results;

    public CsvExportException(String destination, List<RepoStats> results, Exception cause) {
        super("Could not export CSV to '" + (destination == null || destination.isBlank() ? "(not configured)" : destination)
                + "'. " + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage()), cause);
        this.results = Collections.unmodifiableList(new ArrayList<>(results == null ? List.of() : results));
    }

    public List<RepoStats> results() { return results; }
}
