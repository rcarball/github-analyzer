# 📊 GitHub Analyzer (Teaching-Oriented)

[![GitHub Actions CI](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml/badge.svg?branch=master)](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml)

Java (Swing) app to analyze GitHub repository activity for **team projects** (e.g., students).

> 🧭 Teaching focus: these metrics aim to approximate **hands-on coding activity** (especially in Java) in a learning context.  
> Some values may **not match** GitHub *Insights → Contributors* (see “📐 Metrics” and “❓ FAQ”).

---

## ✨ What the app does

- 🌳 Repository tree (left panel).
- 🧾 When you select a repo, shows **repository-level** metrics.
- 👥 Per-author table with commits/lines metrics (focused on `.java`).
- 🧩 Summary of **file types** in the default-branch snapshot.
- 🔄 Incremental GitHub downloads, local recalculation and background update checks.
- 🔗 Explicit per-repository authorship assignments and optional declared team size.

---

## Repository layout

- `src/`, `test/`: application and test sources.
- `lib/`: bundled compilation and test dependencies.
- `resources/`: versioned icons/templates plus ignored local configuration and data.
- `scripts/`: build and test entry points for macOS/Linux (`.sh`) and Windows (`.bat`).
- `docs/`: [developer onboarding](docs/ONBOARDING.md) and third-party notices.
- `build/`: regenerable files: `eclipse/`, `test-classes/`, `jar-classes/`, and `fat/`.
- `dist/`: ignored runnable JARs and checksums. Historical local binaries may be
  kept under `dist/archive/`; published versions belong in GitHub Releases.
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
the **Refresh from GitHub** button re-reads them, so new tokens or repositories
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
java -jar dist/github-analyzer-1.3.1.jar
```
This builds a **self-contained** versioned JAR under `dist/` (currently `dist/github-analyzer-1.3.1.jar`): the application, the icons
**and all third-party libraries are bundled inside** — no `lib/` folder is needed
to run it. On first run, the app creates a `resources/` folder next to the JAR
(`dist/resources/` when running the local build).
For an existing configuration, keep that folder with its `config.properties` and
`repositories.txt` next to the JAR; you can launch it from any directory (paths
resolve relative to the JAR's location). Those files stay
**external and editable** and are never bundled, since they hold your token and
student data.

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
in the installation directory. Build and release scripts package only the JAR
and its checksum, and never copy local credentials or statistics into it.

### Option C — Compile and run from source (CLI)
```bash
# macOS / Linux  (Windows: use ';' as the classpath separator)
javac --release 17 -cp "lib/*" -d build/eclipse $(find src -name "*.java")
java  -cp "build/eclipse:lib/*" es.deusto.prog3.githubanalyzer.Main
```

### CSV export compatibility
`stats.csv` uses UTF-8 (with BOM) and `;` as its separator so it opens cleanly in
common spreadsheet applications. Text fields are quoted, including values with
semicolons, quotes or line breaks. Values that could be interpreted as spreadsheet
formulas are exported as literal text.

### Run the tests
```bash
./scripts/run-tests.sh          # Windows: scripts\run-tests.bat  (or "Run as > JUnit Test" in Eclipse)
```

### Continuous integration
[GitHub Actions](https://github.com/rcarball/github-analyzer/actions/workflows/java-ci.yml)
runs on every push and pull request targeting `master`. It verifies formatting,
runs the automated test suite with Java 17 and builds the runnable JAR.

`master` requires a pull request, a successful **Test and package** check against
its latest commit, and resolved review conversations. Force pushes and branch
deletion are blocked. No approval is mandatory while this is a single-maintainer
project.

### Publish a release
The version in `CITATION.cff` controls both build scripts. Update its `version`
and `date-released` through a pull request before creating a release tag.
Examples elsewhere in this guide use the current release; substitute the version
you are publishing.

After merging the version change, tag the corresponding commit on `master`:
```bash
git switch master
git pull --ff-only origin master
git tag -a v1.3.2 -m "GitHub Analyzer v1.3.2"
git push origin v1.3.2
```

The **Publish release** workflow checks that the tag matches `CITATION.cff` and
belongs to the history of `master`, runs the tests with Java 17, then publishes
the JAR, its SHA-256 checksum and generated release notes. Only the publishing
job has write permissions. Existing releases are never overwritten.

For a rehearsal, run **Publish release → Run workflow** from the `master` branch
and select an existing tag. This validates and packages it without publishing.
The workflow supports both historical tags with scripts and JAR output at the
repository root, and the current `scripts/` + `dist/` layout. Verified artifacts
are always collected in `dist/`.

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

### 3) Manage authorship

The app opens with locally regenerated statistics and checks repository updates in
the background when credentials are configured. The tree suffix `(N)` is the number
of unique commits not downloaded yet, deduplicated across branches; `(0)` means no
new commits and `(?)` means the check failed. The check reads metadata only. It does
not download changed files or alter local original data. Branch changes can exist
with zero new commits (for example after branch deletion or moving a head backwards);
these are shown as `(0; changes pending)` so they are not mistaken for an up-to-date download.
The repository panel provides horizontal scrolling when needed to read long names
and update suffixes in full, including while background checks update the labels.

**Refresh from GitHub** downloads missing commits in the background. After the first
complete download it follows each branch head and fetches only unknown SHAs and their
missing parents, including side histories introduced by merges. Existing commit
records remain unchanged. Default-branch files are pinned to the checked head SHA;
unchanged blob contents are reused. A failed download never advances its checkpoint
or overwrites the previous complete original data.

Select a repository and click **Manage authorship…** to work entirely locally:

1. Select identities using Ctrl/Cmd or Shift and click **Unify selected identities**.
   Their work is assigned to the first selected person's group.
2. Select a person below to highlight all their identities. In the lower table,
   **Visible name**, **Email** and **GitHub account** offer dropdowns of distinct
   values from that person's grouped identities. Email and account choices exclude
   empty values and are read-only when none exist. You can also type a custom visible
   name or use **Rename person…**. Choices are saved per repository; the selected email
   is used in derived statistics and CSV. **Separate selected identities** splits
   aliases again; contact values fall back to an available value if the selected
   identity leaves the group. **Change participation…** sets member/teacher/excluded roles.
3. Review Java commits, observed shares and the expected team split in the local
   preview. Teachers and excluded accounts stay visible but do not enter the team share.
4. **Restore original identities** removes groupings and restores separate original
   identities with their initial teacher/member roles. This is a draft until applied.
5. **Apply and recalculate locally** saves assignments and regenerates statistics
   and CSV without contacting GitHub. **Cancel** discards the draft.

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
  Earliest/latest commit dates found in analyzed history.  
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

### 👤 Per-author table metrics

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
  First/last date of non-merge commit considered for that user.

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
GitHub enforces API **rate limits** (roughly 5,000 requests/hour with a token, and
only ~60/hour without one). Analyzing many repositories — or repos with lots of
commits/branches — can hit that limit. The app requires a configured token for any
online refresh, and that token also needs access when repositories are private.
When fewer repositories finish than configured, the app warns you. Retry later or
check token access. Complete original downloads are saved progressively. Failed repositories retain
their previous original data and pending counts; retries resume from those checkpoints.

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
