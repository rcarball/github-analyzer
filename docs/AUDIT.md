# Consolidated technical and efficiency audit

**Project:** GitHub Analyzer (`rcarball/github-analyzer`), Java 17 / Swing

**Consolidated on:** 17 November 2026

**Current review baseline:** commit `6f8ba63` plus the release preparation changes described below.

## 1. Scope and evidence

This document replaces the previous root-level `AUDITORIA.md` and incorporates
the separate efficiency audit. It is the consolidated record for both reviews.

| Source | Baseline | Evidence and limits |
| --- | --- | --- |
| Original technical audit | `8610c28`, 17 November 2026 | Historical code review, 165 passing Java tests, four passing release-layout checks and compiler-warning observations. Its runtime matrix (Java 17 / Temurin 21, macOS / Linux / Windows) was reported for that review; it was not repeated during consolidation. |
| Efficiency, responsiveness and resilience audit (`auditoria-optimizaciones.md`) | Upstream `a3a1eac`, 17 November 2026 | Nine findings from code inspection, with external synthetic measurements for throttling, reachability and serialization. The external benchmark harness is not part of this repository. |
| Consolidation and implementation review | Current baseline above | Source inspection and local offline verification. Distinguishes historical observations, current implementation and unresolved investigations. No live GitHub repository-download or interactive desktop performance test. |

**Status vocabulary:** *Implemented* means the remedy is present in the reviewed
source; it does not imply a published release. *Partial* means part of a finding
has been addressed. *Open* means the opportunity remains. *Investigate* means an
impact or defect still needs confirmation. *Monitor* means no immediate change is
justified by the available evidence.

Historical measurements and test counts are retained as evidence, not presented
as current performance guarantees. No credential values are reproduced here. The
original assertion that a token had never entered Git history has not been
independently revalidated in this consolidation.

## 2. Executive assessment

The application separates saved original repository data, deterministic statistics,
authorship assignments and Swing presentation. Offline fixtures exercise API
failures, incremental histories, local recalculation and persistence. Important
existing controls include filtered deserialization, CSV text escaping, temporary
file replacement and preservation of the previous complete download on failure.

The two most actionable efficiency findings have now been implemented:

- Synchronization cancellation propagates interruption, cancels submitted tasks,
  interrupts the executor and bypasses final statistics/CSV publication.
- Expensive-call admissions use a shared, interruptible throttle with a monotonic
  clock and a minimum interval of 120 ms, preventing concurrent waiters from
  receiving the same admission slot.

Several historical recommendations are also already implemented: environment
credentials, macOS JDK discovery, current release metadata, dependency inventory,
SBOM generation and a dedicated dependency-audit workflow. They should not be
counted as new work.

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
| E1 | Propagate cancellation into download tasks | High | High: cancellation and replacement refreshes | Low | Implemented for 1.4.0 |
| E2 | Enforce spacing of expensive API calls | High | High: shared API budget | Low | Implemented for 1.4.0 |
| U1 | Confirm and, if needed, bound network timeouts and retries | Potentially high | High: prerequisite for predictable recovery | Low to investigate; remedy TBD | Investigate first |
| E4 | Resume partially completed repository downloads | High | Medium–high for large or unreliable downloads | Medium–high | Open; next resilience project |
| E3 | Download commit details concurrently within one repository | High potential | Medium–high for one dominant repository | Medium | Open; benchmark before implementation |
| E5 | Use a recursive tree listing for source discovery | Medium | Medium; higher for deep source trees | Medium | Open; evaluate after network behavior |
| E9 | Avoid repeated projections and cache source metrics | Low–medium | Medium for repeated authorship editing | Low–medium | Partial; optimize only after profiling |
| E6 | Reuse active-commit reachability across operations | Low | Low–medium for very large histories | Medium with current record design | Partial; defer |
| E7 | Reduce full-result writes under the shared lock | Low at current sizes | Low; grows with repository count | Low–medium | Open; preserve progressive-save behavior |
| E8 | Compress or separate large serialized snapshots | Low at measured size | Low until size limit is approached | High | Monitor |
| U2 | Check remaining local-load paths for UI blocking | Unconfirmed; medium if reproduced | Medium | Low to inspect; remedy TBD | Investigate; initial paths checked |
| T1 | Offer credentials without plaintext config storage | Medium | High on shared or backed-up machines | Low | Implemented; optional local token storage remains |
| T2 | Keep release metadata consistent with tags | High for a release | High at release time | Low | Historical mismatch resolved; ongoing release check |
| T3 | Discover a JDK on macOS when JAVA_HOME is unset | Medium | Medium for terminal users | Low | Implemented |
| T4 | Remove stale test-count claims from onboarding | Low | Medium for documentation accuracy | Low | Historical stale count removed |
| D1 | Track and audit vendored dependency contents | Medium | High for maintenance | Medium | Implemented inventory, SBOM and scan workflow; retain upkeep |
| D2 | Consider a build manager and pin remaining CI action references | Low–medium | Low for build migration; medium for CI maintenance | Medium | Optional follow-ups; separate from D1 |
| C1 | Address historical serialization / constructor warnings | Low / unconfirmed | Low without a demonstrated defect | Low–medium | Monitor; re-run compiler analysis before changes |

## 4. Efficiency and resilience findings

### E1. Cancellation did not stop repository downloads — implemented

The original `synchronizeRepositories` loop caught `InterruptedException` through a
broad `Exception` handler, then used `shutdown()` rather than interrupting the
pool. A cancelled UI worker could leave downloads consuming API quota.

The current implementation restores the interrupt flag, propagates cancellation,
cancels submitted futures and calls `shutdownNow()` on abnormal termination.
Workers check interruption before analysis and result publication; a cancellation
guard prevents late derived-result publication after cancellation is registered.
Checks before saving a completed snapshot reduce late writes. Completed results
saved before cancellation remain available.

Verification: `DownloadCancellationTest` exercises an interrupted in-flight reader,
queued work, preservation of the caller's interrupt flag, absence of final writes
and an already-interrupted load. This is cooperative cancellation: a network or
filesystem operation that ignores interruption may still take time to return.
It is not a guarantee that an already-running I/O operation stops immediately.

### E2. Concurrent throttle calls could leave in bursts — implemented

The old timestamp exchange recorded arrival rather than actual admission. The
external audit reported 40 calls from four threads completing in about 970 ms,
with a minimum gap of 0 ms, versus 4,680 ms for 39 intervals of 120 ms.

`HeavyCallThrottle` now serializes admissions using an interruptible fair lock,
`System.nanoTime()` and the actual last admission time. The network request runs
outside the lock, preserving concurrent in-flight requests. Delayed wake-ups do
not accumulate slots that let subsequent waiters through together. The existing
two-permit changed-file semaphore remains in place.

Verification: `HeavyCallThrottleTest` checks four concurrent callers / 40 admissions
with a simulated clock (39 intervals totaling 4,680 ms), delayed wake-ups and
interruption. These are admission tests, not live network timing measurements.
The throttle applies to the expensive-call paths that invoke it, not every GitHub
request, and does not guarantee avoidance of all server-side rate limits.

### E3. Commit details are sequential within each repository — open

`downloadSnapshot` reads changed-file details sequentially in the initial history
and unknown-parent traversal. Parallelism currently exists between repositories
(up to four tasks); a single large repository cannot fully use that pool.

Evaluate bounded batches of independent commits while retaining the shared
throttle and two-request semaphore. Preserve SHA deduplication, all merge parents,
branch reachability, cancellation and deterministic complete snapshots. Avoid
nested pools that multiply concurrency or block waiting for work on the same pool.
The corrected throttle is a prerequisite now present. Actual speedup is unmeasured
and depends on request latency relative to the shared admission interval.

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

The current `StatisticsCalculator.calculate` resolves active history once and
passes that list to `personStats`; the claimed repeated traversal inside those
two methods no longer applies. Validation during raw load/save and separate
operations can still repeat the work.

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

### E9. Repeated projection and uncached source metrics — partial

The final synchronization pass already passes `confirmedResults` to
`readLocalData(false, confirmedResults)`, reusing fresh calculations. Selective
refresh also retains its initially loaded local results. Therefore, a blanket
claim that every successful repository is recalculated again is inaccurate for
the current source. Progressive writes and the final statistics/CSV publication
remain intentional; overlap with E7 should not be counted twice.

Source metrics are still scanned on each full `calculate`, including authorship
changes. However, the scanner has already been optimized to count lines and
markers in one pass without per-line strings or match-result allocations.
[Onboarding](ONBOARDING.md) records separate local before/after measurements
and their limits. This reduces the urgency of adding a cache.

If profiling justifies caching, key on validated content identity, bound memory,
handle legacy empty blob SHAs and preserve cancellation and exact marker/line
semantics. Do not change the saved original format solely to add a cache.

## 5. Security, privacy and persistence

| Area | Consolidated observation | Remaining action |
| --- | --- | --- |
| GitHub token | Historical review identified plaintext config storage. `Configurator` now gives nonblank `GITHUB_TOKEN` precedence without saving it into configuration or exposing it in the editor. Stored config tokens remain supported; POSIX configuration permissions are restricted to the owner. | Keep credentials and journals out of Git; use only the access needed for the intended repositories. Local files and backups still need appropriate protection. No current secret-content or full-history scan was performed. |
| Student and authorship data | Runtime repository lists, exports, raw data and authorship files are ignored. Per-repository filenames use a digest of the URL. | A hashed filename does not encrypt student data or snapshot contents. Retain ignore rules and handle exports/backups as local data. |
| Deserialization | `DataManager` and `RawDataStore` apply class allowlists and resource limits before reading objects. Raw loads validate repository identity and active history. | Preserve regression coverage; these controls reduce risk without constituting a security certification. |
| CSV formulas | `DataManager.csvTextCell` quotes text, escapes embedded quotes and prefixes an apostrophe for formula-leading characters after leading-whitespace inspection. | Continue routing text columns through this helper; no interactive spreadsheet test was repeated. |
| File replacement | Stores write completed temporary files before replacement, using atomic moves where supported. Configuration changes have a private undo journal and an interprocess lock. | Retain recovery and rollback tests. This does not provide a universal power-loss guarantee or cover arbitrary simultaneous manual edits. |

## 6. Build, dependencies, CI and release status

The project intentionally uses vendored JARs and portable shell/Windows scripts
rather than Maven or Gradle, supporting offline use and direct Eclipse import.
`build-jar` targets Java 17, packages runtime dependencies and icons, excludes the
test runner, removes conflicting signatures and includes license notices. The
historical report estimated the bundled JAR at about 4.4 MB and vendored libraries
at about 7.5 MB; artifact sizes were not remeasured here.

| Direct bundled component | Reviewed inventory version | License |
| --- | --- | --- |
| commons-io | 2.22.0 | Apache-2.0 |
| commons-lang3 | 3.21.0 | Apache-2.0 |
| github-api | 1.330 | MIT |
| jackson-annotations | 2.21 | Apache-2.0 |
| jackson-core | 2.21.7 | Apache-2.0 |
| jackson-databind | 2.21.7 | Apache-2.0 |
| junit-platform-console-standalone (test only) | 6.1.3 | EPL-2.0 |

These are local inventory versions, not a claim that they are the newest releases.
The authoritative inventory is [lib/dependencies.lock.json](../lib/dependencies.lock.json);
embedded components and scopes are recorded in [lib/sbom.cdx.json](../lib/sbom.cdx.json) and
[third-party notices](THIRD_PARTY_NOTICES.md).

The historical dependency finding is substantially addressed: offline verification
checks JAR contents/checksums and SBOM consistency; the dedicated dependency-audit
workflow verifies provenance and queries OSV. The final release check also completed an online OSV scan of all 27 components
with no findings or errors. This is a dated result, not a guarantee about future
advisories.
A build-manager migration remains optional. Existing Java CI and release actions
still use version tags; full-SHA pinning of those actions remains a separate
maintenance opportunity from the pinned dependency-audit workflow.

Java CI runs formatting, release-layout, dependency and Java checks before
packaging. The release workflow validates tag format, exact version agreement and
master ancestry, runs tests, packages the JAR and produces/verifies a SHA-256
checksum before publishing. It also supports manual validation without publishing.

The old `CITATION.cff` mismatch (`1.3.1`, dated 8 October 2026, despite later source
changes) is historical. Current metadata is `1.4.0`, dated 17 November 2026, and
the build reads that version. Recheck metadata and tag agreement for each release;
no claim about the latest published tag or release is made here. Both macOS shell
entry points now discover a JDK when `JAVA_HOME` is unset and respect an explicit
selection.

## 7. Architecture, documentation and unresolved checks

The historical audit highlighted separation of domain models, persistence,
background processing and presentation. Offline recalculation and explicit
authorship rules remain central to the architecture. Concurrency controls include
up to four repository tasks, two simultaneous changed-file expansions and the
corrected shared expensive-call throttle.

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

**Compiler warnings:** The original `-Xlint:all` review recorded serialization
warnings for interface-typed `RepoStats` fields, a potential constructor
`this-escape` warning in `MainWindow` and cosmetic Swing serialization warnings.
These were not rerun in this consolidation. Reconfirm with the selected compiler
before changing serialized layouts or suppressing diagnostics.

**Documentation:** The old onboarding claim of 89 tests is absent from the current
file. Keep dated verification counts distinct from evolving suite size. Licensing
remains Apache-2.0 for project code, with third-party and icon exceptions detailed
in `LICENSE`, `NOTICE` and `docs/THIRD_PARTY_NOTICES.md`. Source headers disclose
AI assistance; this is provenance information, not an independent correctness claim.

## 8. Verification record

| Check | Evidence available for this consolidation |
| --- | --- |
| Java regression suite: `./scripts/run-tests.sh` | Most recent run in this work session: 269 tests passed, 28 containers successful, zero failed/skipped/aborted tests; approximately 19.4 s. Includes five new throttle/cancellation tests. This count includes current working-tree implementation changes. |
| Release compatibility: `python3 scripts/check-release-layout.py` | Re-run during consolidation: four tests passed, covering legacy/current layouts, failed-test propagation and rejection of stale/missing artifacts. |
| Inventory: `python3 scripts/dependencies.py verify` | Re-run during consolidation: seven JARs and 27 SBOM components verified. |
| Dependency audit regression tests: `python3 scripts/check-dependencies.py` | Re-run during consolidation: 24 offline tests passed, including inventory integrity and simulated vulnerability-service failures. |
| Final release package and online dependency checks | JAR built and inspected; test classes, benchmark classes and local credentials excluded. All seven JARs matched Maven Central; OSV completed for 27 components with zero findings/errors. |
| Original technical suite | Historical only: 165 tests / 20 containers passed in approximately 7.018 s at `8610c28`. |
| External performance measurements | Reported figures preserved in E2, E6 and E8; harness not imported and benchmarks not rerun. |
| Not performed | Live GitHub failure/rate-limit tests, interactive GUI/Excel checks, cross-platform rerun, current lint analysis and cross-platform packaged-release execution. |

Offline tests establish behavior under fixtures; they do not establish production
throughput, immediate interruption of all connectors or absence of all defects.

## 9. Recommended next steps

1. Keep E1/E2 and their regression tests together when reviewing the current changes.
2. Establish effective network timeout/retry behavior (U1) and collect real workload
   measurements: repository size, request latency/count, failed-work loss and local
   recalculation time.
3. Choose E4 for resilience if lost downloads are the main problem; choose bounded
   E3 concurrency if single-repository latency dominates. Measure benefits against
   the corrected throttle and keep complete-snapshot guarantees.
4. Evaluate recursive source listing (E5), with completeness checks and fallback.
5. Revisit E6/E7/E9 only when profiling identifies them as material. Retain E8 as a
   size-triggered migration decision and investigate U2 if startup/UI delays occur.
6. Continue release-version, dependency-inventory and documentation upkeep. Avoid
   treating implemented historical recommendations as unresolved backlog items.
