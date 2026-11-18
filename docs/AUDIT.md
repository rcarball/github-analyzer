# Consolidated technical and efficiency audit

**Project:** GitHub Analyzer (`rcarball/github-analyzer`), Java 17 / Swing

**Consolidated on:** 17 November 2026

**Review baseline:** commit `6f8ba63` plus the 1.4.0 release preparation changes.

**Pending-item cleanup:** 18 November 2026. Resolved findings have been removed;
original identifiers are retained for traceability. This cleanup does not constitute
a new runtime audit.

## 1. Scope and evidence

This document tracks only unresolved findings, remaining work on partially addressed
findings, optional follow-ups and investigations from the consolidated technical
and efficiency reviews.

| Source | Baseline | Evidence and limits |
| --- | --- | --- |
| Original technical audit | `8610c28`, 17 November 2026 | Historical code review and compiler-warning observations. Its runtime matrix was not repeated during consolidation. |
| Efficiency, responsiveness and resilience audit (`auditoria-optimizaciones.md`) | Upstream `a3a1eac`, 17 November 2026 | Code inspection and external synthetic measurements. The external benchmark harness is not part of this repository. |
| Consolidation review | Review baseline above | Source inspection and local offline verification. No live GitHub repository-download or interactive desktop performance test. |

**Status vocabulary:** *Partial* means remaining work on a finding that has been
partly addressed. *Open* means the opportunity remains. *Investigate* means an
impact or defect still needs confirmation. *Monitor* means no immediate change is
justified by the available evidence. Optional follow-ups are not required fixes.

Historical measurements are evidence, not current performance guarantees.

## 2. Executive assessment

The main remaining opportunities concern recovery of partially downloaded work,
network timeout/retry behavior, single-repository download throughput and source
listing overhead. Their benefit depends on repository size, network latency and
failure frequency. Small local CPU or serialization optimizations rank lower.

## 3. Impact, relevance and effort register

**Impact** estimates the benefit when the affected situation occurs. **Relevance**
estimates its importance for the current teaching-oriented desktop workload.
**Effort** includes implementation, regression checks and compatibility work:
*Low* is a localized change; *Medium* spans a component or needs careful concurrency
validation; *High* affects persistence formats or recovery semantics. These are
planning estimates, not measured delivery times. Priorities express recommended
order for remaining work, not vulnerability severity.

| ID | Finding / action | Impact | Current relevance | Effort | Status / priority |
| --- | --- | --- | --- | --- | --- |
| U1 | Confirm and, if needed, bound network timeouts and retries | Potentially high | High: prerequisite for predictable recovery | Low to investigate; remedy TBD | Investigate first |
| E4 | Resume partially completed repository downloads | High | Medium–high for large or unreliable downloads | Medium–high | Open; next resilience project |
| E3 | Download commit details concurrently within one repository | High potential | Medium–high for one dominant repository | Medium | Open; benchmark before implementation |
| E5 | Use a recursive tree listing for source discovery | Medium | Medium; higher for deep source trees | Medium | Open; evaluate after network behavior |
| E9 | Cache source metrics during repeated recalculation | Low–medium | Medium for repeated authorship editing | Low–medium | Partial; optimize only after profiling |
| E6 | Reuse active-commit reachability across operations | Low | Low–medium for very large histories | Medium with current record design | Partial; defer |
| E7 | Reduce full-result writes under the shared lock | Low at current sizes | Low; grows with repository count | Low–medium | Open; preserve progressive-save behavior |
| E8 | Compress or separate large serialized snapshots | Low at measured size | Low until size limit is approached | High | Monitor |
| U2 | Check remaining local-load paths for UI blocking | Unconfirmed; medium if reproduced | Medium | Low to inspect; remedy TBD | Investigate; initial paths checked |
| D2 | Consider a build manager and pin remaining CI action references | Low–medium | Low for build migration; medium for CI maintenance | Medium | Optional follow-ups |
| C1 | Address historical serialization / constructor warnings | Low / unconfirmed | Low without a demonstrated defect | Low–medium | Monitor; re-run compiler analysis before changes |

## 4. Efficiency and resilience findings

### E3. Commit details are sequential within each repository — open

`downloadSnapshot` reads changed-file details sequentially in the initial history
and unknown-parent traversal. Parallelism currently exists between repositories
(up to four tasks); a single large repository cannot fully use that pool.

Evaluate bounded batches of independent commits while retaining the shared
throttle and two-request semaphore. Preserve SHA deduplication, all merge parents,
branch reachability, cancellation and deterministic complete snapshots. Avoid
nested pools that multiply concurrency or block waiting for work on the same pool.
Actual speedup is unmeasured and depends on request latency relative to the
shared admission interval.

### E4. No partial checkpoint during a repository download — open

`analyzeRepo` saves the original snapshot only after download completion. Failure
late in a large download loses the newly fetched work from that attempt; the
previous complete snapshot is retained. Current incremental updates can reuse
that previous complete history, but cannot resume a failed intermediate attempt.

Design a separate incomplete-download checkpoint with explicit completeness and
branch-head semantics. Never expose a partial history as a valid original to
statistics or authorship tools. Test interruption, corrupt checkpoints, changed
remote heads, merge parents, safe promotion and restart. Retry/backoff may help
transient errors but is not equivalent to resumable checkpoints; investigate U1
before adding retries that could duplicate library behavior.

### E5. Source discovery uses directory-by-directory listing — open

`downloadFiles` lists directories and reads changed Java file contents. It already
reuses saved source entries whose nonblank blob SHA is unchanged, and skips the
source download when the default-branch head is unchanged.

A recursive tree request could reduce listing overhead. It would still require
content reads for changed Java blobs. Before adopting it, check the bundled
library API, incomplete/truncated tree responses, fallback behavior and the same
commit-ref pinning as current downloads. Preserve file-type counts for non-Java
files and path case. No call-count or end-to-end benchmark was run here.

### E6. Active-commit reachability is recomputed — partial

`RepositorySnapshot.activeCommits()` rebuilds a SHA map and traverses parents from
branch heads. The external synthetic audit reported 60–140 ms per call for
100,000 commits and estimated about five calls per analysis.

The remaining opportunity is reuse across raw load/save validation and separate
operations. Do not assume five traversals still occur per analysis; that historical
estimate has not been remeasured against the current implementation.

A Java record cannot declare an additional per-instance transient cache field.
The suggested lazy-field implementation therefore requires a different design.
Prefer operation-scoped reuse first; a representation or serialization change
needs compatibility tests and is not a trivial low-risk patch. Retain detection
of missing parents and reachability after removed or rewritten branches.

### E7. Progressive result persistence holds a shared lock — open

Each completed repository writes the growing result list under `resultLock`,
followed by a final write. With similarly sized entries, total serialized entries
grow quadratically with repository count. The original audit assessed present
`RepoStats` volume as small; no current contention measurement is available.

Measure before moving persistence to a single writer or batching it. Preserve
progressive recovery, ordering, failure reporting and the guarantee that older
writes cannot overwrite newer complete results. Simply removing intermediate
writes would change recovery behavior.

### E8. Large snapshots use Java serialization — monitor

The external synthetic benchmark used 100,000 commits and 400 Java files of about
20 KB each: 25.8 MB on disk, approximately 1.1 s to save and 0.7 s to load. These
figures were considered acceptable and were not reproduced in this review.

Original Java text is retained for offline recalculation. `RawDataStore` enforces
a 256 MiB file-size limit and a deserialization byte limit, alongside depth,
reference and array bounds. Larger source/history combinations can exceed those
limits. Consider compression or splitting text from history only when real
snapshots justify migration complexity; keep bounded decompression, atomic
replacement, compatibility and recovery tests.

### E9. Uncached source metrics during recalculation — partial

Source metrics are still scanned on each full `calculate`, including authorship
changes. Profile repeated authorship edits before adding a cache; local
measurements and their limits are recorded in [Onboarding](ONBOARDING.md).

If profiling justifies caching, key on validated content identity, bound memory,
handle legacy empty blob SHAs and preserve cancellation and exact marker/line
semantics. Do not change the saved original format solely to add a cache.
Do not count progressive result writes again here; those are covered by E7.

## 5. Build and CI follow-ups

### D2. Build manager and remaining CI action references — optional

Consider a build-manager migration only if its maintenance benefit justifies the
change. Preserve offline use, portable shell/Windows entry points and direct
Eclipse import.

Existing Java CI and release actions still use version tags. Pinning those actions
to full commit SHAs remains a separate maintenance opportunity.

## 6. Unresolved investigations

**U1 — Network behavior:** The effective connection/read timeouts and retry policy
of bundled `github-api` 1.330 and its connector have not been established here.
Inspect the actual dependency and connector configuration, then test delayed,
failed and interrupted requests before deciding on application-level bounds or
backoff. Do not infer infinite timeouts or lack of retries from their absence in
`GitHubDataLoader` alone.

**U2 — Local loading and the EDT:** Initial local loading in `Main.main` happens
before `SwingUtilities.invokeLater`, so that path does not run on the EDT, though
it can delay initial window appearance. Configuration-triggered local reloading
runs in a background `RefreshWorker`; refresh and authorship recalculation also
use workers. Remaining synchronous constructor/listener disk access and large-data
responsiveness have not been exhaustively traced or interactively profiled.
The old approximate line-number reference is not evidence of a current EDT defect.

**C1 — Compiler warnings:** The original `-Xlint:all` review recorded serialization
warnings for interface-typed `RepoStats` fields, a potential constructor
`this-escape` warning in `MainWindow` and cosmetic Swing serialization warnings.
These were not rerun in this consolidation. Reconfirm with the selected compiler
before changing serialized layouts or suppressing diagnostics.

## 7. Pending verification

The following checks were not performed during consolidation and remain relevant
to confirming the open findings:

- Live GitHub delayed-request, failure, interruption and rate-limit tests (U1).
- Real workload measurements: repository size, request latency/count, failed-work
  loss and local recalculation time (E3–E9).
- Interactive GUI checks with large local datasets (U2), and interactive spreadsheet
  checks of CSV exports.
- Current compiler warning analysis (C1).
- Cross-platform runtime and packaged-release execution checks.
- Secret-content and full-history scanning; the historical assertion that a token
  had never entered Git history was not independently revalidated.

Existing offline fixture results do not establish production throughput or
immediate interruption of all connectors. No checks were rerun for this
pending-item cleanup.

## 8. Recommended next steps

1. Establish effective network timeout/retry behavior (U1) and collect real workload
   measurements: repository size, request latency/count, failed-work loss and local
   recalculation time.
2. Choose E4 for resilience if lost downloads are the main problem; choose bounded
   E3 concurrency if single-repository latency dominates. Measure benefits against
   the shared throttle and keep complete-snapshot guarantees.
3. Evaluate recursive source listing (E5), with completeness checks and fallback.
4. Revisit E6/E7/E9 only when profiling identifies them as material. Retain E8 as a
   size-triggered migration decision and investigate U2 if startup/UI delays occur.
5. Assess optional D2 follow-ups separately from runtime optimization and confirm
   compiler warnings (C1) before changing serialized layouts.
