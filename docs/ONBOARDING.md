# Onboarding — GitHub Analyzer

Java (Swing) tool that analyzes GitHub repository activity to help assess
**individual contribution** in team projects (course context). This guide gets a
new contributor productive quickly and records the conventions the codebase relies on.

> For end-user documentation (metrics meaning, badges, FAQ) see [README.md](../README.md).

All commands below are run from the repository root. For the directory layout,
see the [README](../README.md#repository-layout).

---

## 1. Prerequisites
- **Java 17+** (developed/tested on newer JDKs; compile targets 17).
- No Maven/Gradle: all dependencies are vendored under `lib/`.
- A non-empty `github.user` field and a **GitHub Personal Access Token** with *read*
  access to the repos you want to analyze; both are required for online refresh.

## 2. First-time setup
Config and data files are **git-ignored on purpose** (secrets + student PII). Start
from the templates:

```bash
cp resources/config.properties.example resources/config.properties
cp resources/repositories.txt.example  resources/repositories.txt
```

Then edit `resources/config.properties`:
- `github.user`, `github.token` — your account + read-only token.
- `teacher.user`, `teacher.email` — the instructor account (excluded from share math).

Keep comments on separate lines: Java properties files treat trailing `#` text as
part of the value. Relative paths resolve from the working directory in Eclipse/CLI
and from the JAR's directory when packaged. The local `dist/resources` link shares
the default `resources/...` paths; use absolute paths to share custom locations
outside that directory.

And list the repositories to analyze in `resources/repositories.txt`
(one `URL[;GROUP][;TEAM_SIZE]` per line; comma-separated input is also accepted).

> ⚠️ **Never commit a real token or any `stats.csv`/`stats.dat`.** These paths are in
> `.gitignore`; keep them that way. Tokens must be revoked if ever exposed.

## 3. Run
- **Eclipse:** use project-root working directory, output folder `build/eclipse`, and
  compiler compliance/release **17**. A newer installed JDK (such as 21) can provide
  the runtime while compilation targets Java 17. Run `es.deusto.prog3.githubanalyzer.Main`.
- **Runnable JAR (any OS):**
  ```bash
  ./scripts/build-jar.sh            # Windows: scripts\build-jar.bat
  java -jar dist/github-analyzer-1.4.0.jar
  ```
  The JAR already bundles all runtime library dependencies and the icons. On first run it creates `resources/`
  next to itself (`dist/resources` for a local build); keep that external folder
  there once it contains your configuration. See the README for sharing the existing
  project configuration through a local link.
  The JAR can be launched from any directory because its resource paths resolve next
  to the JAR.
- **From source (CLI):**
  ```bash
  javac --release 17 -cp "lib/*" -d build/eclipse $(find src -name "*.java")   # Windows: classpath sep is ';'
  java  -cp "build/eclipse:lib/*" es.deusto.prog3.githubanalyzer.Main
  ```

## 4. Tests
```bash
./scripts/run-tests.sh             # Windows: scripts\run-tests.bat   (or "Run as > JUnit Test" in Eclipse)
```
JUnit 6, run via `lib/junit-platform-console-standalone-*.jar`. The runner reports the current test count after each execution.
When you touch domain/loader/metrics logic, add or update tests; the CLI runner is
headless and fast.

Release-workflow compatibility is checked separately with temporary fixtures for
the legacy root layout and the current `scripts/` + `dist/` layout:

```bash
python3 scripts/check-release-layout.py  # Python 3, Bash and sha256sum required
```
CI runs this check before the Java tests; it also verifies failure propagation
and rejects stale artifacts from the other layout.

### Test suite review

Reviewed all 18 JUnit test classes against their production call sites and
regression scenarios, plus the four release-layout checks. The baseline on
`master` had 204 Java tests. This change removes or consolidates four methods
and adds three behavior tests, leaving 203. Test count alone is not a reason to
remove a distinct failure case.

| Area | Decision and reason |
| --- | --- |
| `RawDataPipelineTest`, `GitHubDataLoaderTest` | Remove the 27-commit console-batch test: that verbose output is no longer the normal contract. Keep unique expansion, shared ancestors, every merge parent, unrelated roots, pagination, incremental checkpoints, cancellation and preserved originals in dedicated tests. Retain the legacy entry-point tests because those methods still exist. |
| Download failure fixture | Reduce 27 commits to three: a successful head followed by a failing ancestor still proves full-SHA diagnostics and rejection of partial history, without irrelevant traversal and deliberate API throttling. |
| `RepoStatsTest`, `UserStatsTest`, `SimpleGitUserTest` | Remove the test's hand-written stream aggregation: it tested its own sums, while actual aggregation is exercised by `StatisticsCalculatorTest`. Keep append/deduplication, null handling, normalization, locale independence and derived metrics. Legacy identity classes remain supported. |
| `MainWindowInterpretationTest` | Fold the separate one-eighth case into the existing exact-boundary test, retaining both inputs. Keep the other thresholds, absent-activity guards, team sizes, rounding and overflow cases: they protect different decisions. |
| `DataManagerTest`, `RawDataStoreTest` | Move the snapshot-version getter/setter check into an actual serialization round trip. Remove the unused `shouldStoreRefresh` helper and its test; real failed/empty refresh tests still verify that valid caches and CSVs are preserved. Retain deserialization allowlists, malformed types, format versions, historical records, recovery and atomic writes. |
| `StatisticsCalculatorTest`, `ContributionMetricsTest` | Keep calculation and role/share tests: low-level formulas, projection of saved originals and displayed classification protect separate layers. Full email matching prevents unrelated people from being excluded as teachers. |
| `AuthorshipFormTest`, `AuthorshipDiscoveryPanelTest`, `AuthorshipStoreTest` | Keep editor/reordering/merge interactions, background work, late-result cancellation, assignments and persistence. Similar-looking cases exercise different UI paths or timing boundaries. |
| `RepositoryConfigTest`, `ConfiguratorTest`, `ConfigurationFormTest`, `SettingsTransactionTest`, `MainWindowRefreshTest` | Keep parsing, metadata, unreadable configuration, atomic multi-file rollback, crash recovery and UI lifecycle tests. A passing parser does not prove that an editor saves the captured values or restores controls after failure. |
| Release-layout checks | Retain both supported layouts and both negative cases (failed tests and stale/missing artifacts). |

New tests cover optional detailed diagnostics, completion only after a successful
statistics write (including a reported write failure), and an actual failed atomic
cache save preserving the previous bytes. Existing console tests now assert
bounded repository summaries while retaining API-read and file-preservation
checks. Both command-line runners print a JUnit summary rather than a success tree.

Local verification: baseline 204/204 passed in 9.581 s; revised suite 203/203
passed in 7.674 s (JUnit execution time, one run each, not a benchmark). Two
additional temporary fault-injection checks confirmed that retained tests fail
when the exact one-eighth boundary becomes inclusive of the lower band, or when
teacher email matching uses only the local part. No injected code was retained.
The four release-layout checks and runnable JAR build also passed. Live GitHub,
interactive windows and the Windows runner were not exercised locally.

## 5. Architecture (packages under `src/es/deusto/prog3/githubanalyzer/`)
- `Main` — entry point (load raw data → recalculate locally → open GUI → check update counts in background).
- `GitHubDataLoader` — incremental download and metadata-only update checks via `github-api`.
- `StatisticsCalculator` — local projection of immutable `RepositorySnapshot` records into statistics.
- `RawDataStore` — filtered, versioned, atomic storage of complete original downloads.
- `domain/` — statistics, `RepositoryConfig`, original `AuthorIdentity`, `AuthorSummary` and `AuthorshipConfig`.
- `persistence/` — `Configurator` (reads `config.properties`), `DataManager`
  (binary output + CSV export), `AuthorshipStore` (atomic local rule persistence), `ContributionMetrics` (**single source of truth** for
  teacher detection and team-churn shares).
- `gui/` — `MainWindow`, `ConfigurationForm` and the detached `AuthorshipForm` editor and cancelable `AuthorshipDiscoveryPanel` (Swing).

## 6. Conventions & key decisions (important, easy to get wrong)
- **Contribution share is team-relative.** `share = userChurn / teamChurn`, where
  `teamChurn` = churn of active contributors **excluding the teacher**. Badge thresholds
  compare `share` to `expected = 1/n`, with declared team size taking precedence over
  the resolved active-contributor count. Excluded accounts also stay out. Use
  `ContributionMetrics` — do **not** recompute a repo-wide denominator, or you reintroduce
  a bias against students.
- **Merges vs. unique commits.** Repo "total commits" includes merges (display only);
  per-user Java commits exclude merges and only count `.java` changes. `RepoStats`
  exposes `getMergeCommits()` / `getNonMergeCommits()` so the numbers reconcile.
- **Snapshot vs. history scope.** Java LOC, file types and external-reference markers
  come only from GitHub's default branch. Commit history traverses all known branches
  and deduplicates by SHA, so it can include commits not present in that snapshot.
- **Originals and projections are separate.** UI author discovery and recalculation must
  never construct a GitHub client or mutate `RepositorySnapshot`. Derived output is
  disposable. A first download is required for legacy installations without raw data.
- **Person display preferences are local.** Persist visible names and selected emails/
  GitHub accounts with authorship settings. Dropdowns contain distinct grouped values;
  contact fields omit blanks and fall back to a current grouped value after a split.
  Older settings without contact selections retain automatic defaults. Changing display
  preferences must not change grouping, roles, contribution counts or original downloads.
- **Incremental checkpoints are per branch.** Reuse known SHAs; recursively obtain all
  unknown parents, including old side branches introduced by a new merge. A timestamp
  or a single last SHA cannot represent all branches. Pin source snapshots to the
  checked default-branch head; reuse unchanged blobs. Persist only complete downloads.
- **Startup checks are metadata-only.** Count unique unknown SHAs with paginated
  comparisons, falling back to history IDs when a base cannot be compared. Do not read
  file diffs or persist originals during checks. Detect default-branch and visibility
  changes even with zero unknown SHAs. Ignore stale background UI checks after downloads.
- **Recover unreadable originals only on explicit download.** Preserve a byte-identical,
  uniquely named copy under `raw.directory/recovery/` before rebuilding in full. Abort
  if preservation fails; retain the damaged original until a complete replacement is
  ready. Local reads and metadata checks must never start recovery.
- **Retain originals after rewritten histories.** Original records are append-only;
  current projections use reachability from downloaded heads. An empty branch set
  has no current commits. Restoring original identities only changes assignments.
- **Configuration paths are user-owned.** The Config dialog preserves custom
  `repositories.file`, `stats.file`, `stats.csv`, `authorship.directory` and `raw.directory` paths.
  Validate repository syntax before writing any settings; unreadable lists remain protected.
- **Explicit identities, stable people.** Original name/email/login tuples are grouped
  by a stable length-prefixed SHA-256 key (trim/case normalization only). No accent,
  name, login or email-local-part heuristic joins different tuples. Saved assignments
  map keys to stable person IDs, visible names and roles. Unknown identities get
  separate defaults; teacher settings initialize roles but explicit roles win.
  Compute statistics from the assigned original commits, not sums of old UserStats:
  distinct Java paths must be unioned across aliases. Never key people by display name/email.
- **Detached author editor.** The form edits a copy, commits pending table edits before
  confirmation, and preserves rules for absent authors. Commit the other table's
  editor before changing assignments or row order. Discovery opens an immediate
  loading view with progress and cancellation; ignore late results after cancellation.
  Configuration reads, discovery and reprocessing run outside the EDT. Refresh/config/authorship controls stay locked through completion.
  Recalculate locally before saving rules; retain the old view on failure. Discovery
  cancellation must not change rules or original data. Visible unify/separate/restore
  actions operate on a detached draft and preview local Java shares.
- **Derived results.** Rebuild from original records and current rules, with deterministic
  sorting and distinct-path unions. Last complete downloads survive network failures;
  their timestamp and the tree's pending counts distinguish them from current GitHub data.
- **CSV export is spreadsheet-safe.** Use `DataManager.csvTextCell` for every text
  field: it quotes separators, quotes and line breaks, and makes formula-like values
  literal. Keep metric values numeric; the export is UTF-8 with BOM, `;` separators,
  decimal commas and CRLF record endings. Share columns remain unquoted fractions
  with six decimals, not percentage strings. `NumberFormats` owns CSV fraction and
  UI percentage presentation; all UI percentages use `25,00 %`, independent of the
  default locale. Do not feed display rounding back into metric calculations.
- **Legacy deserialization is filtered.** `DataManager` uses an `ObjectInputFilter`
  (allow-list + limits). If you add a serialized field type outside `java.util`/`java.lang`
  /`…domain`, extend the filter pattern.
- **External-reference markers** (`IAG`, `FUENTE-EXTERNA`) match as **whole words**
  (`\b…\b`) — see `GitHubDataLoader.countExternalMarkers`.
- **Dates.** Repository overview timestamps use `yyyy-MMM-dd - HH:mm`; contributor
  table dates use `yyyy-MM-dd`, both with `Locale.ROOT`. CSV dates use `yyyy/MM/dd`
  with `Locale.ROOT` as well, keeping ASCII digits and Gregorian years even on a
  computer configured with another calendar or numeral system.
- **Resources** (icons) load from the **classpath** first (`/images/…`), filesystem
  fallback for the IDE — required for the JAR to work.
- **GUI errors** are surfaced to the user (dialogs), not swallowed with `printStackTrace`.

## 7. Recent improvements
The current version improves correctness, robustness, portability and usability:

- Contribution shares, badge calculations, merge-commit accounting and external-reference
  markers use the documented rules consistently in the GUI and CSV export.
- Derived output is rebuilt from preserved original data. Legacy deserialization helpers retain their allow-list, but application workflows regenerate statistics from the separate raw-data store.
- The runnable JAR loads icons from the classpath, while the IDE keeps a filesystem fallback.
- The GUI includes user-visible error dialogs, `yyyy-MM-dd` dates, sortable contributor rows,
  `%`-churn mini-bars and contribution-aware row shading.
- The project includes automated tests and a command-line JUnit runner, including offline API failures, explicit author assignments, editor interactions and team-size calculations.

## 8. Release workflow
- Both build scripts read the version from `CITATION.cff`.
- All changes to `master` must pass **Test and package** through a pull request.
- After merging the release version and date, push its `vMAJOR.MINOR.PATCH` tag.
- **Publish release** validates ancestry/version, tests and packages with Java 17,
  and publishes the JAR and checksum. Manual runs only rehearse an existing tag.
- Historical tags keep their own scripts: the workflow selects the root or
  `scripts/` entry points and collects the resulting JAR in `dist/` for either layout.

## 9. Gotchas
- When packaged, resource paths resolve next to the JAR (and `resources/` is created on
  first run). From the IDE or loose class files, relative resource paths resolve from the
  current working directory.
- The configuration form and refresh lifecycle have headless tests; verify visual
  changes by running the app.
- `github-api` calls count against GitHub rate limits; large repo sets can be throttled.


## 8. Audit follow-up (2026-11-17)

The Antigravity and Claude audits were checked against the source. Version 1.4.0
prepares these changes; setting the version does not publish a release.

- Teacher defaults require a complete email address or the configured account;
  matching only the email local part no longer excludes students.
- Contribution badges compare exact ratios, including both balanced boundaries,
  without display rounding or overflowing products.
- A completely failed refresh preserves both derived files. An explicitly empty
  repository list can still clear both outputs.
- Git metadata is displayed as plain text in contributor and authorship tables,
  including combo-box choices. Tree icons are cached, root tooltips reset, removed
  selections cannot reload the old repository, and overview times use 24 hours.
- Configuration and repository writes use completed temporary files. POSIX config
  permissions are owner-only, including existing installations. `GITHUB_TOKEN`
  takes precedence at runtime and never enters the saved configuration or editor.
  Combined saves now use a private undo journal and an interprocess lock; interrupted
  saves restore the previous config/list pair on startup before loading settings.
- Repository deduplication ignores URL scheme and case while retaining the first
  spelling, preserving the URL-based keys of existing downloads and author rules.
  `.` and `..` path segments are invalid.
- Active history is resolved once per statistics calculation. Authorship previews
  calculate in a cancellable background worker and discard obsolete results;
  action errors appear in a dialog. Closing configuration disposes the window.
- GitHub's documented [commit file limit](https://docs.github.com/en/rest/commits/commits#get-a-commit)
  is handled conservatively: a response of 3,000 or more files rejects that download
  instead of silently recording potentially incomplete work.
- Username/email equality uses `Locale.ROOT`. macOS shell scripts discover a JDK
  when `JAVA_HOME` is unset and respect an explicitly selected JDK.

Reviewed suggestions that do not justify changing behavior automatically:

| Observation | Decision |
| --- | --- |
| CSV decimals in Spanish Excel | Export decimal commas, semicolons, UTF-8 BOM and CRLF records. Shared number formatting unifies all displayed percentages. Tests check complete numeric parsing using Spanish conventions and identical bytes under Spanish, English, Arabic and Thai locales. Interactive Excel validation has not been performed. |
| Commit versus author date | Both dates are retained. Repository history uses committer dates; individual non-merge activity uses author dates. Legacy records remain readable with committer-date fallback; explicit refresh completes their active author dates once, retaining original file data. Unknown dates are omitted from extrema. |
| Discovery cancellation | Already calls `SwingWorker.cancel(true)` and ignores late results; existing tests cover an uninterruptible source. Local discovery may take time to return from filesystem I/O; cancellation does not permit a late save. |
| Manually edited group/team size | Closing the authorship editor synchronizes the reloaded metadata, including on cancellation. Metadata projections preserve complete metrics and do not mutate the previous view. Applying authorship also updates metadata for the other exported results. |
| Full initial history per branch | Optimized: list the default branch once, then follow unknown parent IDs from other heads until reaching stored commits. Explore every merge parent and unrelated roots; do not stop a chronological history list at its first known SHA. Read parent IDs after commit file metadata has been populated, without eagerly resolving parent objects. |
| Vendored libraries, SBOM/CVE scanning, CI action SHA pinning | Dependency inventory, SBOM and OSV scanning are now implemented (see below). Migration to a build manager and SHA pinning of the pre-existing CI/release actions remain separate follow-ups. |
| Legacy `DataManager` methods | Retain the legacy loading API and its deserialization regression tests. Removing it does not fix a current user-facing defect. |
| `RepoStats` ordering versus equality | Retain group/name presentation sorting and URL identity; document that natural order differs from equality for changed metadata. Do not use this mutable presentation model as a sorted identity set. |
| Serialization/compiler warnings | No serialized field layout changes or cosmetic suppressions were introduced; keep compatibility with existing local data. |

Validation covers offline regression tests, release-layout checks and packaging.
The live GitHub API and interactive GUI were not exercised.


The contributor activity window uses `Commit.activityTimestamp()`: captured author
metadata, or the retained committer date when unavailable. `Commit.timestamp()`
keeps its original committer semantics. The added nullable author-date field keeps
old five-field serialized records readable; `null` means an old record awaiting
metadata completion and `-1` means GitHub supplied no author date. No network call
is made during offline projection or deserialization.

The configuration editor captures and validates Swing values on the event thread,
then saves and recalculates in a background worker. An interrupted combined save
leaves `resources/.settings-pending.properties`, an owner-only undo journal that
can contain old credentials. Never commit it. Recovery restores both original
files before loading settings, supports custom repository-list paths and retains
the journal for retry if restoration fails. File replacement is atomic where the
filesystem supports it; recovery covers process interruption, not simultaneous
manual edits or a hardware guarantee against power loss.

## 9. Dependency inventory and audit

`dependencies.lock.json` is the reviewed inventory for the actual JARs shipped in
`lib/`; `sbom.cdx.json` is its reproducible CycloneDX projection. Keep both in sync
with library, Eclipse classpath and application version changes. No Maven or
Gradle installation is required; Eclipse and the existing offline build remain
available as before.

Run `python3 scripts/check-dependencies.py` and
`python3 scripts/dependencies.py verify` for offline validation. The separate
**Dependency audit** workflow verifies Maven Central provenance and queries OSV
on PRs, pushes to `master`, a daily schedule and manual dispatch. Findings and
incomplete scans fail the workflow; the JSON report and SBOM are retained as
workflow artifacts. New workflow actions are pinned to reviewed full commit SHAs.

See [Third-party notices](THIRD_PARTY_NOTICES.md#dependency-review-and-updates) for
the review results, embedded-library coverage and update procedure. The dependency
lock is a verification inventory, not a dependency resolver or build-manager lock.
