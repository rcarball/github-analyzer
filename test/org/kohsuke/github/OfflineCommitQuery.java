package org.kohsuke.github;

import java.util.List;
import java.util.Map;

/** Test-only adapter for package-private API constructors; never opens an HTTP connection. */
public final class OfflineCommitQuery extends GHCommitQueryBuilder {
    private final Map<String, List<GHCommit>> branches;
    private String ref;
    public OfflineCommitQuery(Map<String, List<GHCommit>> branches) {
        super(new GHRepository() {
            @Override GitHub root() { return GitHub.offline(); }
        });
        this.branches = branches;
    }
    @Override public GHCommitQueryBuilder from(String value) { ref = value; return this; }
    @Override public PagedIterable<GHCommit> list() {
        List<GHCommit> commits = branches.getOrDefault(ref, List.of());
        return new PagedIterable<>() {
            @Override public PagedIterator<GHCommit> _iterator(int pageSize) {
                List<GHCommit[]> pages = java.util.Collections.singletonList(commits.toArray(GHCommit[]::new));
                return new PagedIterator<>(pages.iterator(), ignored -> { });
            }
        };
    }
}
