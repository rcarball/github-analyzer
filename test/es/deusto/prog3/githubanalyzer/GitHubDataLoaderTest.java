package es.deusto.prog3.githubanalyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import es.deusto.prog3.githubanalyzer.GitHubDataLoader.RawIdentity;

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
