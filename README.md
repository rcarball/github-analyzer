# 📊 GitHub Analyzer (Teaching-Oriented)

Java (Swing) app to analyze GitHub repository activity for **team projects** (e.g., students).

> 🧭 Teaching focus: these metrics aim to approximate **hands-on coding activity** (especially in Java) in a learning context.  
> Some values may **not match** GitHub *Insights → Contributors* (see “📐 Metrics” and “❓ FAQ”).

---

## ✨ What the app does

- 🌳 Repository tree (left panel).
- 🧾 When you select a repo, shows **repository-level** metrics.
- 👥 Per-author table with commits/lines metrics (focused on `.java`).
- 🧩 Summary of **file types** in the default-branch snapshot.
- 🔄 Refresh from GitHub or work **offline** using a cache (`stats.dat`).

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
user or token is not set — opens a **⚙ Config** dialog so you can fill in your GitHub user, token,
teacher account and the repository list from inside the app. You can reopen it any
time with the **⚙ Config** button.

You can also edit the two files by hand (see the **Configuration** section below);
the **Refresh from GitHub** button re-reads them, so new tokens or repositories
take effect without restarting. To prepare the files up front from the templates:

```bash
cp resources/config.properties.example resources/config.properties
cp resources/repositories.txt.example  resources/repositories.txt
```

### Option A — From the IDE (Eclipse)
Run the main class `es.deusto.prog3.githubanalyzer.Main`.

### Option B — Runnable JAR (any OS)
```bash
./build-jar.sh          # Windows: build-jar.bat
java -jar github-analyzer.jar
```
This builds a **self-contained** `github-analyzer.jar`: the application, the icons
**and all third-party libraries are bundled inside** — no `lib/` folder is needed
to run it. On first run, the app creates a `resources/` folder next to the JAR.
For an existing configuration, keep that folder with its `config.properties` and
`repositories.txt` next to the JAR; you can launch it from any directory (paths
resolve relative to the JAR's location). Those files stay
**external and editable** and are never bundled, since they hold your token and
student data.

### Option C — Compile and run from source (CLI)
```bash
# macOS / Linux  (Windows: use ';' as the classpath separator)
javac --release 17 -cp "lib/*" -d bin $(find src -name "*.java")
java  -cp "bin:lib/*" es.deusto.prog3.githubanalyzer.Main
```

### CSV export compatibility
`stats.csv` uses UTF-8 (with BOM) and `;` as its separator so it opens cleanly in
common spreadsheet applications. Text fields are quoted, including values with
semicolons, quotes or line breaks. Values that could be interpreted as spreadsheet
formulas are exported as literal text.

### Run the tests
```bash
./run-tests.sh          # Windows: run-tests.bat  (or "Run as > JUnit Test" in Eclipse)
```

---

## ⚙️ Configuration

### 1) `resources/config.properties`

```properties
github.user=_USERNAME_                         # Required configuration field (not used for authentication)
github.token=_TOKEN_                           # Required for online refresh; use a read-only token
update.from.github=_<yes|no>_                  # yes: online refresh | no: offline (use stats.dat)
repositories.file=resources/repositories.txt   # Repo list
stats.file=resources/stats.dat                 # Binary cache of last refresh
stats.csv=resources/stats.csv                  # CSV export

# (Optional) Exclude teacher account from expected-share calculations
teacher.user=_TEACHER_USERNAME_
teacher.email=_TEACHER_EMAIL_
```

Custom paths for the repository list, binary cache and CSV export are supported.
Saving user/token/teacher settings from the Config dialog preserves those paths.

### 2) `resources/repositories.txt`
One URL per line (GROUP-ID is optional):

```txt
https://github.com/OWNER/REPO1;GROUP-ID1
https://github.com/OWNER/REPO2;GROUP-ID2
```

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

> Identity: uses GitHub login when available; otherwise derived from commit author info.  
> Some identities may be merged (e.g., `noreply`, same email local-part) to reduce duplicates.

> **Teaching-oriented identity matching:** Students who are new to Git often make commits from different computers or IDEs without configuring the same name and email. To avoid splitting one student's work across several rows, the analyzer deliberately merges likely identities using GitHub login, email local-part and, when supported by another signal, normalized name. This prioritizes recovering a student's full contribution over strict Git-identity matching. In the uncommon case of students with coincident details, review the commit history before drawing conclusions.

- **✅ Java commits**  
  Non-merge commits that touched at least one `.java` file.

- **➕ Java added / ➖ Java deleted**  
  Added/deleted lines in `.java` (sum over non-merge commits).

- **🔁 Java churn**  
  `added + deleted` in `.java`.  
  ✅ Better than “added only” because it accounts for refactors and fixes.

- **📊 % Java churn**  
  The user's share of the **team churn** — the total Java churn of the active contributors, **excluding the teacher account**.  
  Team shares sum to 100%. The teacher row shows `-` (excluded from the share model).

- **🧩 Java files**  
  Number of distinct `.java` files modified by the user.

- **📅 First / Last commit (user)**  
  First/last date of non-merge commit considered for that user.

---

## 🧑‍🏫 Teaching interpretation (Badges + AlertFlags)

The GUI adds a quick interpretation per person based on two concepts:

1) **Churn share**: the person's share of the **team churn** (active contributors, excluding the teacher)  
   \[
   share = \frac{userChurn}{teamChurn}
   \]
   where `teamChurn` is the total Java churn of the active contributors (excluding the teacher).

2) **Expected share**: what a balanced split would look like among active contributors (excluding the teacher account)  
   \[
   expected = \frac{1}{n}
   \]
   where `n` is the number of “real” contributors: `userChurn > 0` OR `javaCommits > 0`, excluding teacher.

> Both `share` and `expected` are computed over the **same** population (active
> contributors, excluding the teacher), so they are directly comparable and the
> team shares add up to 100%.

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

With `expected = 1/n` and `share = userChurn/teamChurn` (both over the same
population — active contributors excluding the teacher):

- `veryLow = expected * 0.5`
- `okMin   = expected * 0.8`
- `okMax   = expected * 1.2`

Final badge (contiguous ranges, no gaps):
- **TEACHER** if the user matches `teacher.user` or `teacher.email`
- **VERY_LOW** if `userChurn == 0` OR `javaCommits == 0` OR `share < veryLow`
- **BELOW** if `veryLow <= share < okMin`
- **BALANCED** if `okMin <= share <= okMax`
- **HIGH** if `share > okMax`


### Badge table

| Badge | Label | Meaning |
|------:|------|---------|
| <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/TEACHER.png" width="24" height="24" alt="Teacher account"> | Teacher account | Instructor account; excluded from expected-share calculations |
| <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/VERY_LOW.png" width="24" height="24" alt="Very low contribution"> | Very low / no contribution | Bar chart at a very low level: very low or null Java contribution; check additional evidence |
| <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BELOW.png" width="24" height="24" alt="Below expected contribution"> | Below expected contribution | Downward chart: some Java work, but under the expected share |
| <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BALANCED.png" width="24" height="24" alt="Balanced contribution"> | Balanced contribution | Balance scale: close to expected share for the team size |
| <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/HIGH.png" width="24" height="24" alt="High contribution"> | High contribution | Upward chart: above expected share; may indicate a strong role or imbalance |

---

## AlertFlag (additional signals)

Flags do not change the badge: they are **extra alerts** to inspect patterns.

### <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/AI.png" width="24" height="24" alt="AI/paste-like alert"> AI_PASTE — “AI/paste-like pattern”
- **Triggers when**:
  - `churnPerCommit >= repoAvgChurnPerCommit * 2.5`
  - (with `javaCommits > 0` and `repoAvgChurnPerCommit > 0`)
- **Suggests**: large bursts per commit (mass paste / AI / generated code).  

---

## Examples (how to read the UI)

### Example 1 — Balanced team members
You hover a row and see:

- Username cell: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BALANCED.png" width="24" height="24" alt="Balanced contribution"> `alice`
- Status bar: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BALANCED.png" width="24" height="24" alt="Balanced contribution"> Balanced contribution
- Tooltip (long): shows expected share and no flags

Interpretation:
- Alice’s churn share is within ~±20% of the expected share for the team size.
- No additional signals triggered.

---

### Example 2 — Below expected but still contributing
Row shows:

- Username cell: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BELOW.png" width="24" height="24" alt="Below expected contribution"> `bob`
- Status bar: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BELOW.png" width="24" height="24" alt="Below expected contribution"> Below expected contribution

Interpretation:
- Bob contributes, but the Java churn share is below the expected range.
- Next step: check whether Bob contributed mainly in non-Java files, worked through PR reviews, or had a different role.

---

### Example 3 — Very low / no Java contribution
Row shows:

- Username cell: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/VERY_LOW.png" width="24" height="24" alt="Very low contribution"> `carol`
- Status bar: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/VERY_LOW.png" width="24" height="24" alt="Very low contribution"> Very low / no contribution

Interpretation:
- Carol has no Java churn or no Java commits, or a share well below half the expected one.
- Next step: confirm with the code defense — she may have worked only on non-Java parts, or contributed little.

---

### Example 4 — High contribution
Row shows:

- Username cell: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/HIGH.png" width="24" height="24" alt="High contribution"> `dave`
- Status bar: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/HIGH.png" width="24" height="24" alt="High contribution"> High contribution

Interpretation:
- Dave’s Java churn share is clearly above the expected share for the team size.
- This can mean a strong role — or an imbalance worth discussing with the team.

---

### Example 5 — AI/paste-like burst pattern
Row shows:

- Username cell: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BELOW.png" width="24" height="24" alt="Below expected contribution"> `eva`
- Status bar: <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/BELOW.png" width="24" height="24" alt="Below expected contribution"> Below expected contribution | <img src="https://raw.githubusercontent.com/rcarball/github-analyzer/master/resources/images/AI.png" width="24" height="24" alt="AI/paste-like alert"> AI/paste-like pattern

Interpretation:
- Eva’s total share might be modest, but her commits have unusually high churn per commit.

---

## 📚 Mini glossary

- **LOC (Lines of Code)**: Number of lines in files (here, `.java`) on the default branch. This is a *size snapshot*, not effort.
- **Churn**: `added + deleted` lines. Used as a proxy for “how much code was edited”.
- **Share (churn share)**: A user’s churn divided by the **team churn** (active contributors, excluding the teacher). Team shares sum to 100%; used to compare relative contribution within a team.
- **Expected share**: `1/n`, where `n` is the number of active contributors (excluding teacher). A baseline for “balanced” teams.
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
When fewer repos come back than configured, the app warns you; retry later, use a
token, or work offline with the cached `stats.dat`. Confirmed repositories are saved
progressively; if one repository fails during a refresh, its previous cached entry is
kept instead of being erased.

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

## 📜 License
MIT License — https://opensource.org/license/mit/

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
