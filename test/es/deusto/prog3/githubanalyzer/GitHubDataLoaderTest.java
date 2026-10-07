// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI-GENERATED TEST CASES
 *
 * All test cases in this file were generated entirely with the assistance of
 * the AI tools identified above.
 */
package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.ArrayList;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHCommitQueryBuilder;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import es.deusto.prog3.githubanalyzer.domain.UserStats;
import java.util.List;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader.RawIdentity;
import es.deusto.prog3.githubanalyzer.domain.RepoStats;

/**
 * Unit tests for the author identity-merging logic
 * ({@link GitHubDataLoader#clusterIdentities(List)} and
 * {@link GitHubDataLoader#pickBetterRep(RawIdentity, RawIdentity)}).
 *
 * <p>These are the rules that decide whether two commit authors are treated as
 * the same person, which directly drives the per-user contribution stats.
 */
public class GitHubDataLoaderTest {

    private static RawIdentity id(String name, String email, String login) {
        return new RawIdentity(name, email, login);
    }

    /** True when identities at positions i and j ended up in the same cluster. */
    private static boolean sameCluster(int[] root, int i, int j) {
        return root[i] == root[j];
    }

    private static RepoStats repo(String url, String name) {
        RepoStats repo = new RepoStats();
        repo.setUrl(url);
        repo.setName(name);
        return repo;
    }

    @Test
    public void replacingOneConfirmedRepositoryKeepsTheOthersCached() {
        RepoStats oldA = repo("https://github.com/course/a", "a-old");
        RepoStats oldB = repo("https://github.com/course/b", "b-old");
        RepoStats refreshedA = repo("https://github.com/course/a", "a-new");

        List<RepoStats> updated = GitHubDataLoader.replaceCachedRepository(
                new ArrayList<>(List.of(oldA, oldB)), refreshedA);

        assertEquals(2, updated.size());
        assertSame(refreshedA, updated.get(0));
        assertSame(oldB, updated.get(1), "A failed repository must retain its previous cache entry");
    }

    private static final String AUDIT_URL = "https://github.com/test-audit/repo";

    private static class StubRepository extends GHRepository {
        List<GHContent> contents = List.of(javaContent(false));
        boolean failSnapshot;
        boolean failHistory;
        int snapshotReads;

        @Override public String getFullName() { return "test-audit/repo"; }
        @Override public Date getPushedAt() { return new Date(1000); }
        @Override public Date getCreatedAt() { return new Date(0); }
        @Override public int getSize() { return 10; }
        @Override public boolean isPrivate() { return false; }
        @Override public String getDefaultBranch() { return "master"; }
        @Override public Map<String, GHBranch> getBranches() throws IOException {
            if (!failHistory) return Map.of();
            GHBranch branch = GitHub.getMappingObjectReader().forType(GHBranch.class)
                    .readValue("{\"name\":\"master\",\"commit\":{\"sha\":\"test-sha\"}}");
            return Map.of("master", branch);
        }
        @Override public GHCommitQueryBuilder queryCommits() {
            throw new IllegalStateException("Simulated commit history API failure");
        }
        @Override public List<GHContent> getDirectoryContent(String path, String ref) throws IOException {
            snapshotReads++;
            if (failSnapshot) throw new IOException("Simulated snapshot API failure");
            return contents;
        }
    }

    private static GHContent javaContent(boolean failRead) {
        return new GHContent() {
            @Override public boolean isDirectory() { return false; }
            @Override public String getName() { return "Example.java"; }
            @Override public InputStream read() throws IOException {
                if (failRead) throw new IOException("Simulated file API failure");
                return new ByteArrayInputStream("// IAG\nclass Example {}\n".getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static RepoStats cachedRepo(int snapshotVersion) {
        RepoStats cached = repo(AUDIT_URL, "repo");
        cached.setLastPushTime(1000);
        cached.setFileSnapshotVersion(snapshotVersion);
        cached.setCodeLines(42);
        cached.setGroup("OLD-GROUP");
        return cached;
    }

    @Test
    public void failedSnapshotRejectsReplacementAndAllowsRetryAtSamePush() {
        StubRepository repository = new StubRepository();
        RepoStats cached = cachedRepo(2);
        repository.failSnapshot = true;
        GitHubDataLoader loader = GitHubDataLoader.getInstance();
        assertNull(loader.analyzeRepository(AUDIT_URL, repository, List.of(cached), true));
        assertEquals(42, cached.getCodeLines());
        assertEquals("OLD-GROUP", cached.getGroup());
        repository.failSnapshot = false;
        RepoStats retried = loader.analyzeRepository(AUDIT_URL, repository, List.of(cached), true);
        assertEquals(2, retried.getCodeLines());
        assertEquals(1, retried.getExternalReferences());
        assertEquals(2, repository.snapshotReads);
    }

    @Test
    public void fileFailureAfterSuccessfulReadRejectsPartialSnapshot() {
        StubRepository repository = new StubRepository();
        repository.contents = List.of(javaContent(false), javaContent(true));
        RepoStats cached = cachedRepo(2);
        assertNull(GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cached), true));
        assertEquals(42, cached.getCodeLines());
    }

    @Test
    public void branchHistoryFailureRejectsOtherwiseValidSnapshot() {
        StubRepository repository = new StubRepository();
        repository.failHistory = true;
        assertNull(GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cachedRepo(2)), true));
        assertEquals(1, repository.snapshotReads);
    }

    @Test
    public void failedCommitExpansionCannotBecomeZeroContribution() {
        GHCommit commit = new GHCommit() {
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public String getSHA1() { return "test-sha"; }
            @Override public PagedIterable<GHCommit.File> listFiles() throws IOException {
                throw new IOException("Simulated commit file API failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> GitHubDataLoader.getInstance()
                .computeUserStatsFromCommits(List.of(commit), "student", "student@example.org", new StringBuilder()));
    }

    @Test
    public void emptyCommitFileListRemainsAValidZeroContribution() {
        GHCommit commit = new GHCommit() {
            @Override public List<GHCommit> getParents() { return List.of(); }
            @Override public Date getCommitDate() { return new Date(1000); }
            @Override public PagedIterable<GHCommit.File> listFiles() {
                return new PagedIterable<>() {
                    @Override public org.kohsuke.github.PagedIterator<GHCommit.File> _iterator(int pageSize) {
                        throw new UnsupportedOperationException();
                    }
                    @Override public List<GHCommit.File> toList() { return List.of(); }
                };
            }
        };
        UserStats stats = GitHubDataLoader.getInstance().computeUserStatsFromCommits(
                List.of(commit), "student", "student@example.org", new StringBuilder());
        assertEquals(0, stats.getCommits());
        assertEquals(0, stats.getChurn());
    }

    @Test
    public void oldSnapshotVersionIsReanalyzedWithoutAnotherPush() {
        StubRepository repository = new StubRepository();
        RepoStats refreshed = GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cachedRepo(1)), false);
        assertEquals(1, repository.snapshotReads);
        assertEquals(2, refreshed.getFileSnapshotVersion());
        assertEquals(2, refreshed.getCodeLines());
    }

    @Test
    public void reusingCacheClearsRemovedGroupWithoutReadingFiles() {
        StubRepository repository = new StubRepository();
        RepoStats cached = cachedRepo(2);
        RepoStats result = GitHubDataLoader.getInstance().analyzeRepository(
                AUDIT_URL, repository, List.of(cached), false);
        assertSame(cached, result);
        assertEquals("", result.getGroup());
        assertEquals(0, repository.snapshotReads);
    }

    // ---------------- clusterIdentities: merging rules ----------------

    @Test
    public void mergesBySameLogin() {
        List<RawIdentity> ids = List.of(
                id("Alice A", "alice@opendeusto.es", "alice123"),
                id("A. Alonso", "other@gmail.com", "alice123") // same login, different name+email
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertTrue(sameCluster(root, 0, 1), "Identities sharing a GitHub login must merge");
    }

    @Test
    public void mergesBySameEmailLocalPartAcrossDomains() {
        List<RawIdentity> ids = List.of(
                id("Bob", "b.smith@opendeusto.es", null),
                id("Roberto", "b.smith@gmail.com", null) // same local-part "b.smith"
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertTrue(sameCluster(root, 0, 1),
                "Identities sharing a (non-noreply) email local-part must merge");
    }

    @Test
    public void doesNotMergeNoreplyEmailsByLocalPart() {
        // A GitHub noreply email must NOT be used to merge by local-part.
        List<RawIdentity> ids = List.of(
                id("Foo", "shared@users.noreply.github.com", null),
                id("Bar", "shared@gmail.com", null) // same local-part but the other side is noreply
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertFalse(sameCluster(root, 0, 1),
                "noreply emails must be excluded from email-local-part merging");
    }

    @Test
    public void mergesBySameNormalizedNameWhenAnchored() {
        // Same person: accents/case differ, and one side is "strong" (resolvable email),
        // so the weak name-only identity is anchored onto it.
        List<RawIdentity> ids = List.of(
                id("Jose Perez", "", null),                       // weak (name only)
                id("José Pérez", "jose.perez@opendeusto.es", null) // strong -> anchor
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertTrue(sameCluster(root, 0, 1),
                "Same normalized name must merge when the group has a strong anchor");
    }

    @Test
    public void doesNotMergeSameNameWithoutAnchor() {
        // Two weak, name-only identities with no login/email must NOT be merged
        // just because their names look alike (avoids false positives).
        List<RawIdentity> ids = List.of(
                id("Ana", "", null),
                id("ana", "", null)
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertFalse(sameCluster(root, 0, 1),
                "Name-only matches must not merge without a strong anchor");
    }

    @Test
    public void mergesTransitivelyThroughABridge() {
        // 0 and 1 share a login; 1 and 2 share an email local-part.
        // DSU must place all three in one cluster.
        List<RawIdentity> ids = List.of(
                id("Carol", "c1@gmail.com", "carol"),
                id("Carol C", "carol.c@opendeusto.es", "carol"), // login bridges 0-1
                id("C. C.", "carol.c@outlook.com", null)         // local-part bridges 1-2
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertTrue(sameCluster(root, 0, 1) && sameCluster(root, 1, 2),
                "Merging must be transitive across login and email bridges");
        assertTrue(sameCluster(root, 0, 2), "0 and 2 must end up in the same cluster");
    }

    @Test
    public void keepsUnrelatedIdentitiesSeparate() {
        List<RawIdentity> ids = List.of(
                id("Dan", "dan@gmail.com", "dan"),
                id("Eve", "eve@opendeusto.es", "eve")
        );
        int[] root = GitHubDataLoader.clusterIdentities(ids);
        assertFalse(sameCluster(root, 0, 1), "Unrelated identities must stay separate");
    }

    @Test
    public void handlesEmptyAndSingletonInput() {
        assertEquals(0, GitHubDataLoader.clusterIdentities(List.of()).length);

        int[] one = GitHubDataLoader.clusterIdentities(List.of(id("Solo", "solo@x.com", "solo")));
        assertEquals(1, one.length);
        assertEquals(0, one[0]);
    }

    // ---------------- pickBetterRep: representative-selection rules ----------------

    @Test
    public void pickBetterRepPrefersIdentityWithLogin() {
        RawIdentity withLogin = id("X", "x@gmail.com", "xlogin");
        RawIdentity noLogin   = id("X", "x@gmail.com", null);
        assertSame(withLogin, GitHubDataLoader.pickBetterRep(withLogin, noLogin));
        assertSame(withLogin, GitHubDataLoader.pickBetterRep(noLogin, withLogin), "order must not matter");
    }

    @Test
    public void pickBetterRepPrefersOpendeustoEmail() {
        RawIdentity open  = id("X", "x@opendeusto.es", null);
        RawIdentity other = id("Y", "y@gmail.com", null);
        assertSame(open, GitHubDataLoader.pickBetterRep(open, other));
        assertSame(open, GitHubDataLoader.pickBetterRep(other, open));
    }

    @Test
    public void pickBetterRepPrefersNonNoreplyEmail() {
        RawIdentity real    = id("X", "x@gmail.com", null);
        RawIdentity noreply = id("Y", "y@users.noreply.github.com", null);
        assertSame(real, GitHubDataLoader.pickBetterRep(real, noreply));
        assertSame(real, GitHubDataLoader.pickBetterRep(noreply, real));
    }

    @Test
    public void pickBetterRepFallsBackToLongerName() {
        RawIdentity shortName = id("Jo", "a@gmail.com", null);
        RawIdentity longName  = id("Jonathan", "b@gmail.com", null);
        assertSame(longName, GitHubDataLoader.pickBetterRep(shortName, longName),
                "With all else equal, the longer display name wins");
    }

    // ---------------- countExternalMarkers: external/AI reference markers ----------------

    @Test
    public void countsStandaloneMarkers() {
        assertEquals(1, GitHubDataLoader.countExternalMarkers("// IAG"));
        assertEquals(1, GitHubDataLoader.countExternalMarkers("(IAG)"));
        assertEquals(1, GitHubDataLoader.countExternalMarkers("// FUENTE-EXTERNA: https://example.com"));
        assertEquals(2, GitHubDataLoader.countExternalMarkers("IAG and FUENTE-EXTERNA on one line"));
        assertEquals(2, GitHubDataLoader.countExternalMarkers("IAG ... IAG again"));
    }

    @Test
    public void ignoresMarkersEmbeddedInIdentifiers() {
        // These would all be false positives without word boundaries.
        assertEquals(0, GitHubDataLoader.countExternalMarkers("int DIAGNOSTIC = 0;"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("drawDIAGRAM();"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("String iagValue; // lowercase, not a marker"));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("IAGENERATIVA"));
    }

    @Test
    public void countExternalMarkersHandlesNullAndEmpty() {
        assertEquals(0, GitHubDataLoader.countExternalMarkers(null));
        assertEquals(0, GitHubDataLoader.countExternalMarkers(""));
        assertEquals(0, GitHubDataLoader.countExternalMarkers("nothing to see here"));
    }
}
