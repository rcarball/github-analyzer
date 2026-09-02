# Onboarding — GitHub Analyzer

Java (Swing) tool that analyzes GitHub repository activity to help assess
**individual contribution** in team projects (course context). This guide gets a
new contributor productive quickly and records the conventions the codebase relies on.

> For end-user documentation (metrics meaning, badges, FAQ) see [README.md](README.md).

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
- `update.from.github` — `yes` (online) or `no` (offline, use cached `stats.dat`).
- `teacher.user`, `teacher.email` — the instructor account (excluded from share math).

And list the repositories to analyze in `resources/repositories.txt`
(one URL per line, optional `;GROUP-ID`).

> ⚠️ **Never commit a real token or any `stats.csv`/`stats.dat`.** These paths are in
> `.gitignore`; keep them that way. Tokens must be revoked if ever exposed.

## 3. Run
- **Eclipse:** run `es.deusto.prog3.githubanalyzer.Main`.
- **Runnable JAR (any OS):**
  ```bash
  ./build-jar.sh            # Windows: build-jar.bat
  java -jar github-analyzer-1.3.0.jar
  ```
  The JAR already bundles all runtime library dependencies and the icons. On first run it creates `resources/`
  next to itself; keep that external folder there once it contains your configuration.
  The JAR can be launched from any directory because its resource paths resolve next
  to the JAR.
- **From source (CLI):**
  ```bash
  javac --release 17 -cp "lib/*" -d bin $(find src -name "*.java")   # Windows: classpath sep is ';'
  java  -cp "bin:lib/*" es.deusto.prog3.githubanalyzer.Main
  ```

## 4. Tests
```bash
./run-tests.sh             # Windows: run-tests.bat   (or "Run as > JUnit Test" in Eclipse)
```
JUnit 5, run via `lib/junit-platform-console-standalone-*.jar`. Currently **65 tests**.
When you touch domain/loader/metrics logic, add or update tests; the CLI runner is
headless and fast.

## 5. Architecture (packages under `src/es/deusto/prog3/githubanalyzer/`)
- `Main` — entry point (load cache → optionally refresh from GitHub → open GUI).
- `GitHubDataLoader` — pulls repos/commits/files via `github-api`, resolves & merges
  author identities, computes per-user Java stats. Parallel per-repo analysis.
- `domain/` — `RepoStats`, `UserStats`, `SimpleGitUser` (serializable POJOs).
- `persistence/` — `Configurator` (reads `config.properties`), `DataManager`
  (binary cache + CSV export), `ContributionMetrics` (**single source of truth** for
  teacher detection and team-churn shares).
- `gui/` — `MainWindow` (Swing).

## 6. Conventions & key decisions (important, easy to get wrong)
- **Contribution share is team-relative.** `share = userChurn / teamChurn`, where
  `teamChurn` = churn of active contributors **excluding the teacher**. Badge thresholds
  compare `share` to `expected = 1/n` over the *same* population. Use
  `ContributionMetrics` — do **not** recompute a repo-wide denominator, or you reintroduce
  a bias against students.
- **Merges vs. unique commits.** Repo "total commits" includes merges (display only);
  per-user Java commits exclude merges and only count `.java` changes. `RepoStats`
  exposes `getMergeCommits()` / `getNonMergeCommits()` so the numbers reconcile.
- **Snapshot vs. history scope.** Java LOC, file types and external-reference markers
  come only from GitHub's default branch. Commit history traverses all known branches
  and deduplicates by SHA, so it can include commits not present in that snapshot.
- **Snapshot-cache versioning.** A change to file-snapshot semantics must bump
  `FILE_SNAPSHOT_VERSION` in `GitHubDataLoader`; that makes legacy cached repositories
  refresh once even when their GitHub push timestamp has not changed.
- **Configuration paths are user-owned.** The Config dialog edits credentials,
  teacher settings and repository contents, but must preserve any custom
  `repositories.file`, `stats.file` and `stats.csv` paths from `config.properties`.
- **Identity merging is deliberately permissive.** This is a teaching tool for students
  who may commit from several computers or IDEs without configuring Git consistently.
  `GitHubDataLoader` therefore clusters likely identities by GitHub login, non-noreply
  email local-part, and normalized name when there is another strong anchor. Do not
  replace this with exact matching without reconsidering that use case: it would split
  one student's work across multiple rows. The accepted trade-off is an occasional
  false merge for coincident details; commit history remains the evidence to review.
- **Cache persistence is progressive.** Each repository confirmed by the loader replaces
  only its own entry and is immediately written to the `.dat`. A failed or timed-out
  repository therefore retains its previous cached data. This deliberately favours
  resilience for long classroom-wide refreshes over minimizing writes.
- **CSV export is spreadsheet-safe.** Use `DataManager.csvTextCell` for every text
  field: it quotes separators, quotes and line breaks, and makes formula-like values
  literal. Keep metric values numeric; the export is UTF-8 with BOM and `;` separators.
- **Cache deserialization is filtered.** `DataManager` uses an `ObjectInputFilter`
  (allow-list + limits). If you add a serialized field type outside `java.util`/`java.lang`
  /`…domain`, extend the filter pattern.
- **External-reference markers** (`IAG`, `FUENTE-EXTERNA`) match as **whole words**
  (`\b…\b`) — see `GitHubDataLoader.countExternalMarkers`.
- **Dates.** The GUI displays dates as `yyyy-MM-dd` with `Locale.ROOT`; CSV dates
  use `yyyy/MM/dd` for spreadsheet compatibility.
- **Resources** (icons) load from the **classpath** first (`/images/…`), filesystem
  fallback for the IDE — required for the JAR to work.
- **GUI errors** are surfaced to the user (dialogs), not swallowed with `printStackTrace`.

## 7. Recent changes (audit session)
Security/privacy: stopped tracking secrets/PII (+ `.example` templates), purged leaked
tokens from git history. Correctness: fixed the team-churn share bias (GUI + CSV +
badges), whole-word markers, merge-commit reconciliation. Robustness: progressive cache
persistence that preserves prior data on partial refreshes, plus an allow-list
deserialization filter. Portability/UX: classpath icons + runnable-JAR build scripts,
user-visible error dialogs, `yyyy-MM-dd` GUI dates, sortable user table with `%`-churn
mini-bars and contribution-aware row shading. Tests grew from 12 (2 failing) to **65 green**,
plus a CLI JUnit runner.

## 8. Gotchas
- When packaged, resource paths resolve next to the JAR (and `resources/` is created on
  first run). From the IDE or loose class files, relative resource paths resolve from the
  current working directory.
- The GUI is not covered by unit tests; verify visual changes by running the app.
- `github-api` calls count against GitHub rate limits; large repo sets can be throttled.
