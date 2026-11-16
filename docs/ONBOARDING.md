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
  java -jar dist/github-analyzer-1.3.1.jar
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
JUnit 6, run via `lib/junit-platform-console-standalone-*.jar`. Currently **89 tests**.
When you touch domain/loader/metrics logic, add or update tests; the CLI runner is
headless and fast.

Release-workflow compatibility is checked separately with temporary fixtures for
the legacy root layout and the current `scripts/` + `dist/` layout:

```bash
python3 scripts/check-release-layout.py  # Python 3, Bash and sha256sum required
```
CI runs this check before the Java tests; it also verifies failure propagation
and rejects stale artifacts from the other layout.

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
  literal. Keep metric values numeric; the export is UTF-8 with BOM and `;` separators.
- **Legacy deserialization is filtered.** `DataManager` uses an `ObjectInputFilter`
  (allow-list + limits). If you add a serialized field type outside `java.util`/`java.lang`
  /`…domain`, extend the filter pattern.
- **External-reference markers** (`IAG`, `FUENTE-EXTERNA`) match as **whole words**
  (`\b…\b`) — see `GitHubDataLoader.countExternalMarkers`.
- **Dates.** Repository overview timestamps use `yyyy-MMM-dd - hh:mm`; contributor
  table dates use `yyyy-MM-dd`, both with `Locale.ROOT`. CSV dates use `yyyy/MM/dd`
  for spreadsheet compatibility.
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
