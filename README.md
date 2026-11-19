# 📊 GitHub Analyzer (Teaching-Oriented)

[![GitHub Actions CI](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml/badge.svg?branch=master)](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml)

Java 17 desktop app (Swing) for reviewing contribution to GitHub **team projects**,
especially student programming projects. Download repository history, group the
different author identities belonging to each person, and inspect their Java
activity alongside repository metrics and CSV exports.

> 🧭 Teaching focus: these metrics aim to approximate **hands-on coding activity** (especially in Java) in a learning context.  
> Some values may **not match** GitHub *Insights → Contributors* (see “📐 Metrics” and “❓ FAQ”).

---

## ✨ What the app does

- 🌳 Repository tree (left panel).
- 🧾 When you select a repo, shows **repository-level** metrics.
- 👥 Per-person table with commits/lines metrics (focused on `.java`).
- 🧩 Summary of **file types** in the default-branch snapshot.
- 🔄 Incremental GitHub downloads, local recalculation and background update checks.
- 🔗 Collaborator integration: combine a person's author identities through
  multiple selection or drag and drop, with confirmation, undo and separation.
- 🧑‍🏫 Per-person roles and optional declared team size for interpreting contribution.

### Typical workflow

1. Add repositories in **Config** and configure GitHub credentials for downloading.
2. Use **Refresh all from GitHub** for the first download. Later, use
   **Refresh pending** or right-click a repository to update it individually.
3. Select a repository and open **Manage authorship…**. If one collaborator appears
   under several names, emails or accounts, combine those identities into one
   person, choose their role and apply the changes.
4. Review the per-person metrics and contribution indicators; inspect `stats.csv`
   for the exported results. Existing downloads can be reopened and authorship
   can be adjusted offline.

See [collaborator integration](#collaborator-integration-how-it-works) for the
step-by-step grouping workflow, its effect on metrics and how to reverse it.

The [1.4.2 release notes](docs/releases/1.4.2.md) describe how the runnable JAR
preserves dependency licenses and notices. Download the JAR and verification
files from [release v1.4.2](https://github.com/rcarball/github-analyzer/releases/tag/v1.4.2).

---

## Repository layout

- `src/`, `test/`: application and test sources; `test/benchmarks/` contains
  standalone performance measurements, run manually with the benchmark script.
- `lib/`: bundled compilation and test dependencies, their reviewed inventory
  (`dependencies.lock.json`) and generated SBOM (`sbom.cdx.json`).
- `resources/`: versioned icons/templates plus ignored local configuration and data.
- `scripts/`: build and test entry points for macOS/Linux (`.sh`) and Windows (`.bat`).
- `docs/`: [developer onboarding](docs/ONBOARDING.md), third-party notices and the
  [consolidated audit](docs/AUDIT.md), with priorities by impact, relevance and effort.
  Versioned release notes are kept in `docs/releases/`.
- `build/`: regenerable files: `eclipse/`, `test-classes/`, `jar-classes/`, and `fat/`.
- `dist/`: ignored current-release runnable JAR and checksum; published versions
  and historical binaries belong in GitHub Releases.
- `.github/workflows/`: CI and release automation.

`README.md`, `LICENSE`, `NOTICE`, `CITATION.cff`, and `LICENSES/` stay at the root.
Eclipse workspace metadata stays in the workspace; local `.project`, `.classpath`,
and `.settings/` files remain ignored by Git. Configure Eclipse through its project
settings when importing the project on another machine.

Build and test scripts resolve paths from their own location, so they can also be
invoked by absolute path from another working directory. Examples below assume
you are in the repository root.

---

## 🚀 How to run

### Requirements
- ☕ Java 17+ (required)
- 🗂️ Libraries included under `lib/` for compiling (no Maven/Gradle required); the
  runnable JAR bundles them, so nothing extra is needed to *run* it
- 🔑 A non-empty GitHub user field and token are required for an online refresh;
  use a read-only token with access to the repositories to analyze

### First, configure
On the **first run** the app creates a `resources/` folder with default
`config.properties` and `repositories.txt` next to the app, and — if the required
user or token is not set — opens a **Config** dialog so you can fill in your GitHub user, token,
teacher account and the repository list from inside the app. You can reopen it any
time with the **Config** button.

You can also edit the two files by hand (see the **Configuration** section below);
the GitHub refresh actions re-read them, so new tokens or repositories
take effect without restarting. To prepare the files up front from the templates:

```bash
cp resources/config.properties.example resources/config.properties
cp resources/repositories.txt.example  resources/repositories.txt
```

### Option A — From the IDE (Eclipse)
Run the main class `es.deusto.prog3.githubanalyzer.Main`, using the repository
root as the working directory. Set the compiler compliance/release to **17** and
the output folder to `build/eclipse/`. A newer installed JDK can be used to run
Eclipse and the application while compiling for Java 17.

### Option B — Runnable JAR (any OS)
```bash
./scripts/build-jar.sh          # Windows: scripts\build-jar.bat
java -jar dist/github-analyzer-1.4.2.jar
```
This builds a **self-contained** versioned JAR under `dist/` (currently `dist/github-analyzer-1.4.2.jar`): the application, the icons
**and all third-party libraries are bundled inside** — no `lib/` folder is needed
to run it. On first run, the app creates a `resources/` folder next to the JAR
(`dist/resources/` when running the local build).
For an existing configuration, keep that folder with its `config.properties` and
`repositories.txt` next to the JAR; you can launch it from any directory (paths
resolve relative to the JAR's location). Those files stay
**external and editable** and are never bundled, since they hold your token and
student data.

Both build scripts use the same Java 17+ helper to preserve each runtime
dependency's licenses and notices under `META-INF/licenses/<artifact-version>/`
inside the JAR. No extra build dependency is required. After building, run
`python3 scripts/check-jar-licenses.py` (`python` on Windows) to verify those
texts, runtime classes, icons and exclusions against the source libraries.
CI is configured to run the native test/build scripts and this verification
on Windows, macOS and Linux with JDK 17.

To reuse the existing project configuration, original downloads, authorship rules and output locations when running the local JAR,
create a directory link **once**, if `dist/resources` does not already exist:

```bash
# macOS / Linux, from the repository root after building
ln -s ../resources dist/resources
```

On Windows, from Command Prompt in the repository root:

```bat
mklink /J dist\resources "%CD%\resources"
```

The link is local and ignored by Git; it shares files configured with the default
`resources/...` paths with Eclipse. It does not share other relative locations:
for example, `stats.file=cache/stats.dat` refers to `<project>/cache/stats.dat`
from Eclipse, but `<project>/dist/cache/stats.dat` from the local JAR. Use absolute
paths for custom files outside `resources/` that must be shared between both modes.

For a standalone installation, keep the JAR and its own `resources/` folder together
in the installation directory. The build packages the application and runtime
libraries. Release downloads also include a checksum, dependency inventory and
SBOM; local credentials, configuration and statistics are never included.

### Option C — Compile and run from source (CLI)
```bash
# macOS / Linux  (Windows: use ';' as the classpath separator)
javac --release 17 -cp "lib/*" -d build/eclipse $(find src -name "*.java")
java  -cp "build/eclipse:lib/*" es.deusto.prog3.githubanalyzer.Main
```

### CSV export compatibility
`stats.csv` targets Excel with Spanish (Spain) regional separators: UTF-8 with BOM,
`;` between columns, **`,` as the decimal separator** and CRLF between records.
The output is independent of the computer's language settings. Text fields remain
quoted, including semicolons, quotes and embedded line breaks; formula-looking
text remains escaped.

`JAVA_CHURN_SHARE` and `EXPECTED_SHARE` remain numeric **fractions between 0 and 1**
with six decimals, for example `0,250000`. They are not percentage strings: apply a
percentage cell format in Excel to display 25%. Undefined shares remain `-`.
Columns, their order and the underlying metrics are unchanged. Earlier exports
used a decimal point; scripts reading new exports must now parse a decimal comma.

If Excel uses custom or non-Spanish separators, import through **Data → From
Text/CSV**, selecting UTF-8, `;` and Spanish (Spain) numeric interpretation.
Microsoft documents [regional list/decimal separators](https://support.microsoft.com/es-es/excel/get-started/import-or-export-text-txt-or-csv-files)
and [opening UTF-8 CSV files with BOM](https://support.microsoft.com/en-gb/excel/opening-csv-utf-8-files-correctly-in-excel).

Percentages throughout the application—table cells, bars, tooltips and the
authorship editor—use the same Spanish presentation: **`25,00 %`**, two decimals
and one space before `%`. An unavailable percentage is `—`. Display rounding does
not change contribution shares or badge boundaries.

Repository URLs are deduplicated independently of HTTP/HTTPS and letter case.
The first configured spelling is kept so existing downloads and author assignments
remain accessible. Dot-only path segments are rejected.

### Run the tests
```bash
./scripts/run-tests.sh          # Windows: scripts\run-tests.bat  (or "Run as > JUnit Test" in Eclipse)
```

For the optional real-window download regression check, use a JDK 17 or newer
and a graphical desktop:

- macOS/Linux: `./scripts/check-download-dialogs.sh`. An explicit `JAVA_HOME`
  must point to a complete JDK. Otherwise macOS uses JDK discovery; the `PATH`
  fallback resolves compiler symlinks and uses the runtime from that same folder.
- Windows 11: double-click `scripts\check-download-dialogs.bat`, or run
  `.\scripts\check-download-dialogs.bat` from PowerShell at the project root.
  The console stays open to show the result; add `--no-pause` to skip that wait.
  An explicit `JAVA_HOME` must point to a complete JDK. Otherwise the launcher
  finds `javac.exe` on `PATH` and uses `java.exe` from that same folder; it checks
  Java 17 or newer before running. Project and JDK paths may contain spaces.

Both launchers delegate compilation, child-process control, exit codes and cleanup
to the same Java 17+ controller, `scripts/DownloadDialogLauncher.java`. No Python
or PowerShell installation is needed to run the graphical check. All generated
classes, source lists, configuration and simulated repository data stay in one
private temporary folder. The controller removes `GITHUB_TOKEN` from child
processes and never writes to the real configuration or downloaded data.

The graphical check also starts four separate JVMs with the real application
exit handler: idle close, close during a successful or failed background operation,
and close during a cancelled download. Each must exit by itself with code 0
within 15 seconds. Pending operations must finish their cleanup and cancellation
recovery before exit. A timeout, nonzero exit or missing completion marker fails
the check; terminating a child for cleanup never counts as success. All child
configuration and data are synthetic, and both GitHub token environment variables
are removed from these child processes.

Success prints six `PASS:` lines followed by
`All download dialog checks passed. Temporary test files were removed.` Compilation
and test failures return nonzero exit codes; cleanup failures are also failures.
An ordinary console interruption runs the JVM shutdown cleanup, which stops the
active compiler or test process before deleting its own temporary folder. Cleanup
does not follow symlinks or remove unrelated temporary data. Forcefully killing
the JVM or closing its console can prevent cleanup and leave the private folder.

Run `python3 scripts/check-download-launcher.py` for the ten offline launcher checks.
They require a JDK 17+ and Python 3, but do not open windows. They exercise the
shared controller with real compilation, special paths, failed compilation/tests,
credentials, cleanup failures, symlinks and interruption, plus POSIX JDK discovery.
POSIX-only cases are skipped on Windows; console Ctrl+C and the `.bat` wrapper still
need native Windows validation. The shared controller and graphical checks have
been verified on macOS.

The runner prints totals and failures instead of listing every successful test.
The [test review](docs/ONBOARDING.md#test-suite-review) records which redundant
checks were consolidated and why the remaining regression tests are retained.

To compare large Java source processing with a previous revision, run
`./scripts/benchmark-java-files.sh 4ded5fd` (macOS/Linux, a JDK with thread-allocation
counters). This optional offline probe in `test/benchmarks/` measures loading and
calculation separately; CI does not run these measurements. See the
[measurement method and results](docs/ONBOARDING.md#large-java-source-measurements).

### Continuous integration
[GitHub Actions](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml)
runs on every push and pull request targeting `master`. It verifies formatting,
runs the automated test suite with Java 17 and builds the runnable JAR.

`master` requires a pull request, a successful **Test and package** check against
its latest commit, and resolved review conversations. Force pushes and branch
deletion are blocked. No approval is mandatory while this is a single-maintainer
project.

### Dependency inventory and vulnerability checks

`lib/dependencies.lock.json` records the reviewed Maven identities, licenses and SHA-256
hashes of every JAR in `lib/`, including the components embedded in Jackson and
JUnit. `lib/sbom.cdx.json` is the reproducible CycloneDX 1.6 inventory for the source
distribution; test-only components have `excluded` scope and are still scanned.

```bash
python3 scripts/dependencies.py verify           # Offline: JARs, metadata, local Eclipse classpath if present, and SBOM
python3 scripts/check-dependencies.py            # Offline regression tests
python3 scripts/dependencies.py verify-upstream  # Online: compare bytes with Maven Central
python3 scripts/dependencies.py scan             # Online: check all components against OSV
```

The commands require Python 3.9+ and its standard library, with no extra packages.
The **Dependency audit** workflow runs on pull requests, pushes to `master`, daily
at 06:23 UTC, and manually. It saves the inventory, SBOM and JSON vulnerability
report as workflow artifacts. Any active finding fails the check; an unavailable
service or incomplete response also fails, rather than reporting a clean scan.
The normal **Test and package** check also verifies the inventory and offline tests.

See [Third-party notices](docs/THIRD_PARTY_NOTICES.md#dependency-review-and-updates)
for review evidence, coverage limits and the dependency update procedure.

### Publish a release
The version in `CITATION.cff` controls both build scripts. Update its `version`
and `date-released`, add English notes at `docs/releases/<version>.md`, and
regenerate `lib/sbom.cdx.json` with `python3 scripts/dependencies.py generate`
through a pull request before creating a release tag.
Examples elsewhere in this guide use the current release; substitute the version
you are publishing.

After merging the version change, tag the corresponding commit on `master`:
```bash
git switch master
git pull --ff-only origin master
# Example for the next patch release, after updating metadata and notes to 1.4.3:
git tag -a v1.4.3 -m "Release GitHub Analyzer 1.4.3"
git push origin v1.4.3
```

The **Publish release** workflow checks that the tag matches `CITATION.cff` and
belongs to the history of `master`, runs the tests with Java 17, then publishes
the JAR, its SHA-256 checksum, `dependencies.lock.json` and `sbom.cdx.json`, using
the versioned English release notes from the repository. Only the publishing
job has write permissions. Existing releases are never overwritten.

For a rehearsal, run **Publish release → Run workflow** from the `master` branch
and select an existing tag. This validates and packages it without publishing.
The test/build/checksum steps support both historical tags with scripts and JAR
output at the repository root, and the current `scripts/` + `dist/` layout. New
releases additionally require the current dependency metadata and versioned notes.
The release workflow collects binaries in `dist/`, dependency metadata in `lib/`
and notes in `docs/releases/` before publishing.

To check both layouts locally using the workflow's shell commands (requires
Python 3, Bash and `sha256sum`):

```bash
python3 scripts/check-release-layout.py
```
This check uses temporary fixtures, including failed tests and missing artifacts,
and also runs in CI.


---

## ⚙️ Configuration

### 1) `resources/config.properties`

```properties
# Required configuration field (not used for authentication)
github.user=_USERNAME_
# Required for online refresh; use a read-only token
github.token=_TOKEN_
# Repo list
repositories.file=resources/repositories.txt
# Derived statistics output (regenerated from raw.directory)
stats.file=resources/stats.dat
# CSV export
stats.csv=resources/stats.csv
# Local per-repository authorship assignments
authorship.directory=resources/authorship
# Original incremental downloads
raw.directory=resources/raw-data

# (Optional) Exclude teacher account from expected-share calculations
teacher.user=_TEACHER_USERNAME_
teacher.email=_TEACHER_EMAIL_
```

Custom paths for the repository list, binary output, CSV export, authorship directory and raw-data directory are supported.
Saving user/token/teacher settings from the Config dialog preserves those paths.
Relative paths resolve against the working directory in Eclipse/CLI, or the
directory containing the runnable JAR. Use absolute paths to share custom data
locations between these modes. Put comments on separate lines: a trailing `#`
and its text become part of the value in Java properties files.

### 2) `resources/repositories.txt`
One repository per line, separated with **`;`**. Group and team size are optional:

```txt
https://github.com/OWNER/REPO1
https://github.com/OWNER/REPO2;GROUP-02
https://github.com/OWNER/REPO3;4
https://github.com/OWNER/REPO4;GROUP-04;4
```

A numeric second field is the declared team size; a nonnumeric one is the group.
With three fields, the second is always the group, so `URL;12;4` represents numeric
group `12` with four members; `URL;12;` represents that group without a declared size.
`URL;;4` is also accepted. Sizes must be positive integers. Blank lines and lines
starting with `#` are ignored. Duplicate URLs may supply complementary settings,
but conflicting groups or sizes are rejected with a line number. Existing
`URL;GROUP` entries with nonnumeric groups remain valid. A numeric second field
means team size; numeric groups without a declared size need a trailing separator
(`URL;12;`). Comma-separated input is also accepted for compatibility. Existing
files are not automatically rewritten. Quoted fields and mixed separators on the
same line are not supported.

The semicolon-separated UTF-8 CSV retains its existing columns and appends
`TEAM_SIZE_DECLARED`, `CONTRIBUTORS`, `AUTHOR_IDENTITIES`, `TEAM_SIZE_EFFECTIVE`,
`EXPECTED_SHARE`, `PERSON_ID` and `PARTICIPATION`. An absent declared size is blank;
an undefined expected share or an excluded person's observed share is `-`.
`PERSON_ID` is stable across display-name changes. Consumers assuming an exact
column count must be updated.

During export, active-contributor count, team churn, effective team size and
expected share are calculated once per repository and reused for its rows.
The export uses the same contribution rules as the interface and recalculates
these values on each export, including after role or team-size changes.

CSV exports are written to a unique temporary file in the destination folder.
The previous CSV is replaced atomically only after writing and closing the entire
new export. If creation, generation or replacement fails, the previous file is
kept (or no destination is created), and the temporary is removed when possible.
Filesystems without atomic replacement report an export failure rather than
attempting an unprotected overwrite.

An export error is shown in the interface with its destination and cause, including
at startup, after refresh, and after configuration or authorship changes.
Calculated statistics remain visible; the error does not mark a successful
analysis as pending. If Excel has locked the CSV, close it and retry the operation.

### 3) Synchronization and saved original data

The app opens with locally regenerated statistics and checks repository updates in
the background when credentials are configured. Repository nodes always use the
public/private icon; only the root uses the GitHub icon. Known visibility is kept
when an analysis or download is unavailable and updated by remote metadata checks.
The tree suffix `(N)` is the number of unique commits not downloaded yet,
deduplicated across branches. Up-to-date repositories have no suffix; `(?)` means
the check failed. Branch or metadata changes with no new commits appear as
`(changes pending)`. Pending changes use orange text; missing local data and
confirmed empty repositories use red. Selection remains readable in the system
selection colors. The repository panel provides horizontal scrolling for long labels.

The update check reads metadata only and does not download changed files or alter
local originals. Downloads have two scopes:

- **Refresh all from GitHub** checks and downloads every configured repository.
- **Refresh pending** downloads only repositories with missing local data, failed
  downloads, detected changes or failed/unknown update checks. Confirmed empty
  repositories without remote changes are skipped. This button is disabled when
  no repository needs updating.

Right-click a repository and choose **Refresh this repository from GitHub** to
retry or update just that repository. Selective downloads preserve every other
repository's local results in progressive saves and the CSV. Their follow-up
remote checks are also limited to the requested repositories. Refresh summaries
count only the requested repositories, and the selected repository remains selected.
All refresh actions are locked while another editing or refresh operation is active.

While a refresh runs, a modal dialog shows finished repository
attempts and the latest phase for each repository, including file and commit
counts when available. Failed attempts are identified separately in the details.
An activity indicator remains animated through the final statistics and CSV save.
Use **Cancel download** to stop queued and active downloads. Closing the progress
dialog with its close button first offers **Keep downloading** (the default) or
**Cancel download**. Dismissing this warning also keeps the download running.
The dialog displays “Cancelling…” until active requests have stopped; completed
repository downloads remain saved and are reloaded locally into the main window.
The dialog closes automatically after completion or cancellation.
Use **Refresh pending** afterwards to retry unfinished repositories.
If the main window receives a close request during a download, a warning offers
**Keep downloading** (the default) or **Cancel download and close**. Dismissing the
warning keeps the download running. Confirming requests cancellation and waits for active requests and local recovery to finish before
closing. A settings save or local recalculation also finishes before closing.
Progress is visible when opening the JAR directly, without a console. The bar counts
repository attempts, not elapsed time: repositories can differ greatly in size.

Refreshes download missing commits in the background. The first
download lists the default branch's complete history once, then follows the other
branch heads only through commits not already downloaded. Shared ancestors and
branch aliases do not cause another complete history listing. After that first
complete download it follows each branch head and fetches only unknown SHAs and their
missing parents, including side histories introduced by merges. Existing commit
records remain unchanged. Default-branch files are pinned to the checked head SHA;
unchanged blob contents are reused. A failed download never advances its checkpoint
or overwrites the previous complete original data.

Cancellation propagates to queued and active download tasks and skips final
publication of derived results. Completed saves remain available. Cancellation
is cooperative: an in-flight I/O operation that ignores interruption can take time
to return. Expensive API calls share a 120 ms admission interval and at most two
changed-file expansions run concurrently; downloads can still encounter GitHub
rate limits. There is no partial-download checkpoint within a repository.

An explicit refresh also completes author dates missing from older downloads once,
without enumerating their changed files or reading source contents again. Local
recalculation remains offline. Group and team-size edits update the local display
and expected shares without discarding completed work metrics. Closing the authorship
editor also synchronizes its repository metadata, including after cancellation.
Configuration saves run in the background with editing and closing disabled until
the operation completes.

Update checks reuse completed comparisons with the same base/head SHA pair and
full history-ID queries with the same head within each repository. Comparisons
with different bases remain independent, and every new check reads GitHub again.
These checks do not download file contents or modify the saved originals.

After a batch download, confirmed statistics are reused for the final display
and export, with current group/team settings. Only repositories without a
confirmed result are rebuilt from local originals. A separate local recalculation
still reads the current authorship rules; results are not cached across runs.

The console (Eclipse Console, or the terminal used to launch the app) shows
one start and one completion message per downloaded repository, tagged with
`owner/repository` and a timestamp. Completion includes elapsed time, commits and
people; batch downloads also show the completed-repository count. The final
summary distinguishes confirmed repositories from failures. Update checks and
local recalculations use repository summaries too; the internal recalculation
following a download does not announce each repository again.

Failures retain the repository, cause and full affected commit SHA. Cancelled
update checks are reported as cancellations. A failed statistics save propagates
to the caller, so it cannot produce a successful repository-completion message.
For branch checkpoints, commit batches and file reuse diagnostics, enable
`-Dgithubanalyzer.debug=true` before `-jar` when launching Java (or as a VM argument
in Eclipse). Detailed progress callbacks remain available to callers.


## Collaborator integration: how it works

One person may appear several times because they used different Git author names,
emails or GitHub accounts. Integration lets you explicitly attribute those
identities to the same person **within one repository**. It changes the local
analysis; it does not merge GitHub accounts, change repository permissions or
rewrite commits. The app discovers authors from downloaded commits, rather than
listing everyone who has collaborator access to the repository.

| Term | Meaning in the interface |
| --- | --- |
| **GitHub identities** | Original Git author identities: the name, email and linked GitHub login captured with commits. This is not a count of distinct GitHub accounts. |
| **People** | Resolved people after your local assignments. Every observed person remains visible, including authors with no Java commits. |
| **Contributors** | Distinct people with the **Teammate** role and Java commits, used to infer team size when none is declared. |
| **Team size** | The configured size, if supplied; otherwise the inferred contributor count. |
| **Expected share** | One divided by the declared or inferred team size. |

Different identities remain separate until you combine them. Matching a name,
login or part of an email does not automatically group distinct identities.
Original identity matching uses the complete name/email/login combination,
ignoring case and surrounding whitespace.

Removing a URL from `repositories.txt`, either externally before startup or through
**Config**, removes it from the displayed repositories and subsequent statistics
and CSV exports. The saved original download and authorship assignments are kept
on disk; adding the same URL again reuses them without an initial download.
External file edits while the app is open take effect on the next repository-list
reload, such as a refresh or restart; the file is not watched continuously.

### Combine a collaborator's identities

Select a repository and click **Manage authorship…**. A first complete original
download must exist; the editor loads it locally without a token or network.
The main window and all dialogs keep their normal sizes when they fit, and
shrink only if either dimension exceeds 90% of the current monitor's usable area
(excluding the taskbar and accounting for display scaling). On smaller screens,
scrollbars keep content accessible where needed; larger monitors do not enlarge
the default windows.

1. Find the collaborator in the sortable **People** table; search by name, email or
   GitHub account. Rows show role, identity count, Java commits and observed share.
   Documentation-only and merge-only authors remain visible with zero Java commits.
2. Select the rows belonging to that person with Ctrl/Cmd-click, Shift-click or
   Shift+Up/Down. With at least two selected, click **Combine selected…** to choose
   the **Destination** person in the right-hand panel. The destination keeps its
   person ID and visible name.
3. Review the resulting **Role** and click **Confirm combination**. If the selected
   people have different roles, you must choose one explicitly. The operation moves
   whole groups, including saved aliases absent from the current analysis. You can
   combine an existing group with more identities later.
4. Select the resulting person to review their visible name, representative email,
   GitHub account and role. Contact choices come from their original identities;
   empty contact fields are disabled. **Edit name** allows a custom display name;
   **Done** finishes that edit. Renaming two rows to the same name does not combine
   them.
5. Click **Apply and recalculate locally**, review the change summary and confirm.
   The app saves your repository-local assignments and regenerates statistics and
   CSV from the original download without contacting GitHub.

**Drag-and-drop alternative:** drag one or more selected people onto another
person's row. The outlined target becomes the proposed destination. Dropping only
prepares the same confirmation panel: review the destination and role, then click
**Confirm combination**. **Cancel combination** discards that proposal without
changing assignments or undo/redo history. Sorting or filtering never chooses the
destination for you; drop onto a visible person outside the dragged selection.

**Confirm combination** changes the open draft. **Apply and recalculate locally**
then shows a change summary for confirmation, saves the assignments and rebuilds
the main statistics and CSV. Until you apply, **Cancel** discards the draft;
combining in the editor alone does not save changes. Contribution previews are
calculated in the background while you edit.

### What changes in the metrics?

Suppose Alice appears as `alice-campus` (100 Java churn) and `alice-home`
(200 Java churn), while Bob has 300 Java churn. All three rows are Teammates
with non-merge Java commits. Before integration, the analysis shows three
identities and three people. Combine Alice's two rows into one
**Teammate** and apply:

| Result | Before | After |
| --- | --- | --- |
| Original identities | 3 | 3 |
| People / Java contributors | 3 / 3 | 2 / 2 |
| Alice's Java churn | Split across two rows: 100 and 200 | One row: 300 |
| Alice's observed share | 16,67 % and 33,33 % | 50,00 % |
| Bob's observed share | 50,00 % | 50,00 % |
| Expected share, with no declared team size | 33,33 % | 50,00 % |

Commits and line changes are attributed to the resulting person. A Java file
modified under both aliases is counted once in that person's distinct-file count.
Repository commit totals, source LOC and original author metadata do not change.
If a team size of four is declared, expected share stays at 25,00 % before and
after integration; integration does not change that configured size.

| Role shown in the editor | Effect |
| --- | --- |
| **Teammate** | Eligible for observed team churn shares and, with Java activity, inferred team size. |
| **Teacher** | Visible with their activity, but excluded from team churn shares and inferred team size. |
| **Other** | Visible excluded account, with the same share exclusion as Teacher. |

Changing the role or grouping can change the share denominator and contribution
badges. Excluded people remain in the results, and a declared team size still
controls expected share. Use the grouping for identities known to belong to one
person; grouping different teammates would distort the analysis.

### Correct or reverse an integration

Use **Undo** for a draft operation, **Separate selected identities** for individual
aliases, or **Move to…** to assign aliases to a different existing person. After
saving and reopening, the grouping remains but the previous session's undo history
does not. You can still separate identities or use **Restore original identities…**
and apply again. That restore affects all assignments, names, contact choices and
role overrides for this repository, including saved aliases currently absent from
the active analysis, then rebuilds separate identities with default roles from
the archived original commits. It preserves the downloaded original commits.

**Undo** and **Redo** cover groupings, names, contact choices and roles. Use
Ctrl/Cmd+Z and Ctrl/Cmd+Shift+Z (or Ctrl/Cmd+Y). **Restore session start** recovers
the configuration present when the editor opened and is itself undoable.
**Restore original identities…** is also undoable until you apply the draft.

Assignments do not automatically propagate to another repository. Configure each
repository separately; identical names in different projects are not a global
person directory.

### Saved data and role defaults

A linked GitHub account takes precedence over a freely chosen Git name when
initializing teacher roles. Explicit saved roles take precedence afterwards. New
identities start as separate people; names, logins or email local-parts never cause
an automatic fusion. Temporarily absent identities retain their saved assignments.

Original downloads live in `raw.directory` (default `resources/raw-data/`, ignored
by Git), independently of assignments under `authorship.directory`. Their versioned
files contain immutable original author metadata, SHAs, parents, dates, changed file
paths and additions/deletions, plus the default-branch Java source snapshot. They
contain no credentials or objects from the GitHub API. Commits made unreachable by
a force push or branch deletion are retained in the original archive; current
statistics cover commits reachable from the latest downloaded branch heads.

Update checks also detect default-branch and public/private visibility changes,
even when there are no new commits. If an original download is unreadable, an
explicit **Refresh from GitHub** first preserves its exact bytes in
`raw.directory/recovery/`, using a unique dated filename, then downloads it again
in full. If the backup fails, reconstruction stops. If the download fails, both
the damaged original and its recovery copy remain; ordinary local reads never
start recovery or overwrite these files.

`stats.dat` and `stats.csv` are derived outputs, never the source of local
recalculation. Existing old statistics lack original commits: **one initial download
is required after this upgrade**. They are not migrated or fabricated into raw data.
After that, author management, restoring identities, team-size changes and local
startup work without a token or network. Only update checks and incremental downloads
require GitHub credentials. `update.from.github` is obsolete, ignored when reading old
properties and omitted when saving settings. Existing local files are not rewritten
until you explicitly save or download through the app. Custom directories must be
kept outside version control and must not be blank.

The overview distinguishes original author identities, resolved contributors with
Java activity, and declared/inferred team size. Its author tooltip shows the original
download timestamp. A declared size changes the expected share only; differences
from observed contributors are flagged. People without commits cannot be inferred.

---

## 🔐 GitHub token

For private repositories and to reduce throttling, use a token with **read** access to the repos.
You can supply it through the `GITHUB_TOKEN` environment variable instead of
`github.token`. A non-blank environment value takes precedence and is never copied
to the configuration dialog or saved to disk. The GitHub user field remains required.
On POSIX systems the configuration file is restricted to its owner (`0600`).
Configuration and repository-list writes replace completed temporary files; a failed
combined save keeps a private undo journal until both files are saved. If the
application is interrupted during that save, the previous pair is restored at
startup. The journal is removed on successful completion.

---

## 📐 Metrics (what they mean)

### 🧾 Repository-level metrics

- **🧱 Total commits (unique)**  
  Number of **unique** commits found (deduplicated by SHA), across analyzed history and known branches.  
  ✅ Global activity indicator.  
  ⚠️ Not the same as “Java commits per person” (see the table). Hover the label to see the breakdown:
  **total = merge commits + non-merge commits**, and the per-person **Java commits** only count
  non-merge commits that changed `.java`.

  > **Why the numbers don’t “add up”** — Example: a repo shows **150** total commits.
  > If **12** are merges, there are **138** non-merge commits. The per-person *Java commits*
  > sum to some value **≤ 138** (e.g. 95), because commits that touched only non-`.java` files
  > (docs, configs, resources) or that were merges are excluded from the Java stats.

- **📅 Creation date**  
  Repository creation date from GitHub.

- **⏱️ First commit / Last commit**  
  Earliest/latest committer dates in the analyzed history; unknown dates are ignored.
  📌 Useful to estimate real working period and detect end-of-period spikes.

- **📄 Java Lines-Of-Code (LOC) (snapshot)**  
  Current lines in `.java` files on GitHub's **default branch** (snapshot).
  📌 Measures size, not effort.

- **🔁 Java churn (added + deleted)**  
  Total added+deleted lines in `.java` from analyzed commits (non-merge only).  
  ✅ Proxy for editing effort.  
  ⚠️ Can be inflated by formatting, generated code, or large pastes.

- **🔗 External references**  
  Occurrences of the standalone markers `IAG` or `FUENTE-EXTERNA` inside `.java` files on the **default branch**
  (matched as whole words, so they are **not** counted inside identifiers like `DIAGNOSTIC`).  
  📌 Useful as a “reference/AI mention” signal (not proof).

> **Metric scope:** File-based metrics (Java LOC, file types and external references) are a snapshot of GitHub's default branch only. Commit-history metrics traverse all known branches, deduplicate commits by SHA, and therefore can include work that is not present in the current default branch.

---

### 👤 Per-person table metrics

> Identity: each row represents one explicitly configured person. Original Git
> identities remain separate until assigned to the same person in Manage authorship.
> File counts are distinct paths across all of that person's original commits;
> merging aliases never sums duplicate paths.

- **✅ Java commits**  
  Non-merge commits that touched at least one `.java` file.

- **➕ Java added / ➖ Java deleted**  
  Added/deleted lines in `.java` (sum over non-merge commits).

- **🔁 Java churn**  
  `added + deleted` in `.java`.  
  ✅ Better than “added only” because it accounts for refactors and fixes.

- **📊 % Java churn**  
  The user's share of the **team churn** — the total Java churn of the active contributors, **excluding teachers and other excluded accounts**.
  Team shares sum to 100%. Teacher and other excluded rows show `-` (outside the share model).

- **🧩 Java files**  
  Number of distinct `.java` files modified by the user.

- **📅 First / Last commit (user)**  
  First/last author date of non-merge commits considered for that user. Rebase and
  cherry-pick committer dates do not move this activity window. Older downloads
  without an author date use their recorded committer date until the next explicit
  GitHub refresh fills the missing dates. If GitHub supplies no author date, the
  committer date remains the fallback.

---

## 🧑‍🏫 Teaching interpretation (Badges + AlertFlags)

The GUI adds a quick interpretation per person based on two concepts:

1) **Churn share**: the person's share of the **team churn** (active contributors, excluding teachers and other excluded accounts)
   \[
   share = \frac{userChurn}{teamChurn}
   \]
   where `teamChurn` is the total Java churn of the active contributors (excluding teachers and other excluded accounts).

2) **Expected share**: what a balanced split would look like for the declared or inferred team size
   \[
   expected = \frac{1}{n}
   \]
   where `n` is the declared team size if provided, otherwise the number of resolved contributors with `userChurn > 0` OR `javaCommits > 0`, excluding teachers and other excluded accounts.

> Observed shares sum to 100% of actual team churn. Declaring a team size changes
> only the expected share, not observed percentages: two equal contributors in a
> declared team of four each have an observed 50% share versus an expected 25%.

> Important: these are **indicators**, not an automatic grading system.  
> Use them to guide review, interviews, and code defense.

---

## ContributionBadge (main level)

Badges are the **main** interpretation result. They appear:
- in the **username cell** (large marker),
- in the **long tooltip**,
- and in the **status bar**.

The GUI displays each icon at 24×24 px. The PNG source images are packaged
with the application and scaled consistently by the GUI, so their appearance
does not depend on emoji support or on a font installed by the operating
system.

### How the badge is computed (rules)

With `expected = 1/n` (declared or inferred size) and
`share = userChurn/teamChurn` (actual contributors, excluding teachers and excluded accounts):

- `veryLow = expected * 0.5`
- `okMin   = expected * 0.8`
- `okMax   = expected * 1.2`

Final badge (contiguous ranges, no gaps):
- **TEACHER** for people assigned the teacher role
- **EXCLUDED** for other excluded accounts (no expected-share interpretation)
- **VERY_LOW** if `userChurn == 0` OR `javaCommits == 0` OR `share < veryLow`
- **BELOW** if `veryLow <= share < okMin`
- **BALANCED** if `okMin <= share <= okMax`
- **HIGH** if `share > okMax`


Commits reaching GitHub's [3,000-file API limit](https://docs.github.com/en/rest/commits/commits#get-a-commit)
are rejected conservatively because completeness cannot be established, including
commits with exactly 3,000 files. The previous complete download is retained.

Badge boundaries use exact ratios; rounding the displayed percentage does not change the badge.

### Badge table

| Badge | Label | Meaning |
|------:|------|---------|
| <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/TEACHER.png?raw=true" width="24" height="24" alt="Teacher account"> | Teacher account | Instructor account; excluded from expected-share calculations |
| <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/VERY_LOW.png?raw=true" width="24" height="24" alt="Very low contribution"> | Very low / no contribution | Bar chart at a very low level: very low or null Java contribution; check additional evidence |
| <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BELOW.png?raw=true" width="24" height="24" alt="Below expected contribution"> | Below expected contribution | Downward chart: some Java work, but under the expected share |
| <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BALANCED.png?raw=true" width="24" height="24" alt="Balanced contribution"> | Balanced contribution | Balance scale: close to expected share for the team size |
| <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/HIGH.png?raw=true" width="24" height="24" alt="High contribution"> | High contribution | Upward chart: above expected share; may indicate a strong role or imbalance |

---

## AlertFlag (additional signals)

Flags do not change the badge: they are **extra alerts** to inspect patterns.

### <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/AI.png?raw=true" width="24" height="24" alt="AI/paste-like alert"> AI_PASTE — “AI/paste-like pattern”
- **Triggers when**:
  - `churnPerCommit >= repoAvgChurnPerCommit * 2.5`
  - (with `javaCommits > 0` and `repoAvgChurnPerCommit > 0`)
- **Suggests**: large bursts per commit (mass paste / AI / generated code).  

---

## Examples (how to read the UI)

### Example 1 — Balanced team members
You hover a row and see:

- Username cell: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BALANCED.png?raw=true" width="24" height="24" alt="Balanced contribution"> `alice`
- Status bar: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BALANCED.png?raw=true" width="24" height="24" alt="Balanced contribution"> Balanced contribution
- Tooltip (long): shows expected share and no flags

Interpretation:
- Alice’s churn share is within ~±20% of the expected share for the team size.
- No additional signals triggered.

---

### Example 2 — Below expected but still contributing
Row shows:

- Username cell: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BELOW.png?raw=true" width="24" height="24" alt="Below expected contribution"> `bob`
- Status bar: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BELOW.png?raw=true" width="24" height="24" alt="Below expected contribution"> Below expected contribution

Interpretation:
- Bob contributes, but the Java churn share is below the expected range.
- Next step: check whether Bob contributed mainly in non-Java files, worked through PR reviews, or had a different role.

---

### Example 3 — Very low / no Java contribution
Row shows:

- Username cell: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/VERY_LOW.png?raw=true" width="24" height="24" alt="Very low contribution"> `carol`
- Status bar: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/VERY_LOW.png?raw=true" width="24" height="24" alt="Very low contribution"> Very low / no contribution

Interpretation:
- Carol has no Java churn or no Java commits, or a share well below half the expected one.
- Next step: confirm with the code defense — she may have worked only on non-Java parts, or contributed little.

---

### Example 4 — High contribution
Row shows:

- Username cell: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/HIGH.png?raw=true" width="24" height="24" alt="High contribution"> `dave`
- Status bar: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/HIGH.png?raw=true" width="24" height="24" alt="High contribution"> High contribution

Interpretation:
- Dave’s Java churn share is clearly above the expected share for the team size.
- This can mean a strong role — or an imbalance worth discussing with the team.

---

### Example 5 — AI/paste-like burst pattern
Row shows:

- Username cell: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BELOW.png?raw=true" width="24" height="24" alt="Below expected contribution"> `eva`
- Status bar: <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/BELOW.png?raw=true" width="24" height="24" alt="Below expected contribution"> Below expected contribution | <img src="https://github.com/rcarball/github-analyzer/blob/master/resources/images/AI.png?raw=true" width="24" height="24" alt="AI/paste-like alert"> AI/paste-like pattern

Interpretation:
- Eva’s total share might be modest, but her commits have unusually high churn per commit.

---

## 📚 Mini glossary

- **LOC (Lines of Code)**: Number of lines in files (here, `.java`) on the default branch. This is a *size snapshot*, not effort.
- **Churn**: `added + deleted` lines. Used as a proxy for “how much code was edited”.
- **Share (churn share)**: A user’s churn divided by the **team churn** (active contributors, excluding teachers and other excluded accounts). Team shares sum to 100%; used to compare relative contribution within a team.
- **Expected share**: `1/n`, where `n` is the declared team size or, when absent, the resolved active-contributor count excluding teachers and excluded accounts.
- **Non-merge commit**: A commit that is not a merge commit. This app focuses on non-merge commits to better approximate authored edits.
- **Deletion ratio**: `deleted / (added + deleted)`. High ratios often indicate refactoring or cleanup.
- **Churn per commit**: `userChurn / javaCommits`. Spikes may indicate large pastes or generated code.
- **End-loaded work**: Activity concentrated near the end of the project period (late commits).

---

## ❓ FAQ

### Why doesn’t this match GitHub *Insights → Contributors*?
GitHub uses its own heuristics (default branch, merges, attribution rules, etc.).  
This app uses a teaching-oriented definition:
- “Java commits” = non-merge commits touching `.java`
- “Java churn” = added+deleted in `.java` from non-merge commits
- “Unique repo commits” = deduplicated by SHA

Goal: **consistency for teaching interpretation**, not to replicate GitHub UI.

### A student shows a very-low or below-expected icon — does that mean they did nothing?
Not necessarily. They may have:
- contributed mostly in **non-Java** files
- merge commits excluded from the Java stats

### Does it reliably detect AI usage?
No. It only flags patterns compatible with large pastes/AI/templates.  
Always confirm with code defense and understanding questions.

### Why did a refresh only analyze some repositories?
GitHub enforces API **rate limits**. Analyzing many repositories — or repos with lots of
commits/branches — can hit that limit. The app requires a configured token for any
online refresh, and that token also needs access when repositories are private.
When fewer repositories finish than requested, the app warns you. Retry later or
check token access. Complete original downloads are saved progressively. Failed repositories retain
their previous original data; retry them with **Refresh pending** or the repository
context menu. A retry reuses the last complete saved repository snapshot. Commits
downloaded only during a failed attempt must be fetched again; partial progress
inside that attempt is not saved.

---

## 🔒 Teaching ethics & privacy

- ✅ Be transparent about what is measured and what isn’t.
- ✅ Use metrics to guide review—not as automatic grading.
- ✅ Consider roles and context (setup, reviews, non-Java work).
- ✅ Allow explanation and additional evidence.
- ✅ Avoid sharing identifiable metrics publicly.
- ✅ Keep `stats.dat` only as long as necessary.

---

## 🧠 Limitations

- Does not measure code quality (correctness, design, style).
- Commit habits differ (many small commits vs few large ones).

---

## 🧾 Credits
- Icons: *Pixel perfect* (Flaticon) — shown in the app footer.

---

## 📜 Licensing, attribution and citation

The Java source code, build scripts and runnable JAR are licensed under the
[Apache License 2.0](LICENSE). Its attribution notice is available in
[NOTICE](NOTICE).

Documentation authored for this project is licensed under
[CC BY 4.0](LICENSES/CC-BY-4.0.md). Third-party libraries and icons are excluded
from those grants and retain their own terms; see
[THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).

For academic or teaching use, GitHub can generate the recommended citation from
[CITATION.cff](CITATION.cff) through the repository's **Cite this repository**
option.

---

## Authorship

Faculty of Engineering, University of Deusto — Academic year 2026-27.

---

## AI assistance and review disclosure

The initial version of this codebase was developed in 2024 with partial
assistance from ChatGPT (OpenAI).

From July to September 2026, the codebase was reviewed and audited using
Claude Code (Anthropic) and Codex (OpenAI).

The resulting version was reviewed, tested, and refined to identify and
correct issues within the scope of the performed verification activities.
