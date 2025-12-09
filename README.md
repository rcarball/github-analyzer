# 📊 GitHub Analyzer (Teaching-Oriented)

Java (Swing) app to analyze GitHub repository activity for **team projects** (e.g., students). It supports **public** and **private** repositories (when the token has access).

> 🧭 Teaching focus: these metrics aim to approximate **hands-on coding activity** (especially in Java) in a learning context.  
> Some values may **not match** GitHub *Insights → Contributors* (see “📐 Metrics” and “❓ FAQ”).

---

## ✨ What the app does

- 🌳 Repository tree (left panel).
- 🧾 When you select a repo, shows **repository-level** metrics.
- 👥 Per-author table with commits/lines metrics (focused on `.java`).
- 🧩 Summary of **file types** present.
- 🔄 Refresh from GitHub or work **offline** using a cache (`stats.dat`).

---

## 🚀 How to run

### Requirements
- ☕ Java 17+ (recommended)
- 🗂️ Libraries included under `lib/` (no Maven/Gradle required)
- 🔑 GitHub token (recommended; required for private repos)

### Main class
- `es.deusto.prog3.githubanalyzer.Main`

### Running (no Maven/Gradle)
- Import as a **Java Project**
- Add `lib/` to the **Build Path**
- Run the `Main` class

---

## ⚙️ Configuration

### 1) `resources/config.properties`

```properties
github.user=_USERNAME_                         # Username (informational)
github.token=_TOKEN_                           # Token (recommended; private repos + fewer limits)
update.from.github=_<yes|no>_                  # yes: online refresh | no: offline (use stats.dat)
repositories.file=resources/repositories.txt   # Repo list
stats.file=resources/stats.dat                 # Binary cache of last refresh

# (Optional) Exclude teacher account from expected-share calculations
teacher.user=_TEACHER_USERNAME_
teacher.email=_TEACHER_EMAIL_
```

### 2) `resources/repositories.txt`
One URL per line:

```txt
https://github.com/OWNER/REPO1
https://github.com/OWNER/REPO2
```

---

## 🔐 GitHub token

For private repositories and to reduce throttling, use a token with **read** access to the repos.

> Tip: refreshing many repos can trigger GitHub rate limits.  
> Use offline mode (`update.from.github=no`) and refresh later.

---

## 📐 Metrics (what they mean)

### 🧾 Repository-level metrics

- **🧱 Total commits (unique)**  
  Number of **unique** commits found (deduplicated by SHA), across analyzed history and known branches.  
  ✅ Global activity indicator.  
  ⚠️ Not the same as “Java commits per person” (see the table).

- **📅 Creation date**  
  Repository creation date from GitHub.

- **⏱️ First commit / Last commit**  
  Earliest/latest commit dates found in analyzed history.  
  📌 Useful to estimate real working period and detect end-of-period spikes.

- **📄 Java Lines-Of-Code (LOC) (snapshot)**  
  Current lines in `.java` files (final snapshot).  
  📌 Measures size, not effort.

- **🔁 Java churn (added + deleted)**  
  Total added+deleted lines in `.java` from analyzed commits (non-merge only).  
  ✅ Proxy for editing effort.  
  ⚠️ Can be inflated by formatting, generated code, or large pastes.

- **🔗 External references**  
  Occurrences of patterns `IAG` or `FUENTE-EXTERNA` inside `.java` files.  
  📌 Useful as a “reference/AI mention” signal (not proof).

---

### 👤 Per-author table metrics

> Identity: uses GitHub login when available; otherwise derived from commit author info.  
> Some identities may be merged (e.g., `noreply`, same email local-part) to reduce duplicates.

- **✅ Java commits**  
  Non-merge commits that touched at least one `.java` file.

- **➕ Java added / ➖ Java deleted**  
  Added/deleted lines in `.java` (sum over non-merge commits).

- **🔁 Java churn**  
  `added + deleted` in `.java`.  
  ✅ Better than “added only” because it accounts for refactors and fixes.

- **📊 % Java churn**  
  The user share of the repository Java churn.

- **🧩 Java files**  
  Number of distinct `.java` files modified by the user.

- **📅 First / Last commit (user)**  
  First/last date of non-merge commit considered for that user.

---

## 🧑‍🏫 Teaching interpretation (Badges + AlertFlags)

The GUI adds a quick interpretation per person based on two concepts:

1) **Churn share**: how much Java churn of the repo is attributed to that person  
   \[
   share = \frac{userChurn}{repoChurn}
   \]

2) **Expected share**: what a balanced split would look like among active contributors (excluding the teacher account)  
   \[
   expected = \frac{1}{n}
   \]
   where `n` is the number of “real” contributors: `userChurn > 0` OR `javaCommits > 0`, excluding teacher.

> Important: these are **indicators**, not an automatic grading system.  
> Use them to guide review, interviews, and code defense.

---

## 🏷️ ContributionBadge (main level)

Badges are the **main** interpretation result. They appear:
- in the **username cell** (large emoji),
- in the **long tooltip**,
- and in the **status bar**.

### How the badge is computed (rules)

With `expected = 1/n` and `share = userChurn/repoChurn`:

- `veryLow = expected * 0.5`
- `okMin   = expected * 0.8`
- `okMax   = expected * 1.2`
- `high    = expected * 1.25`

Final badge:
- **🎓 TEACHER** if the user matches `teacher.user` or `teacher.email`
- **🛑 VERY_LOW** if `userChurn == 0` OR `javaCommits == 0` OR `share < veryLow`
- **🟠 BELOW** otherwise (some contribution, but under expected share)
- **✅ BALANCED** if `okMin <= share <= okMax`
- **🌟 HIGH** if `share >= high`

### Badge table

| Badge | Label | Meaning |
|------:|------|---------|
| 🎓 | Teacher account | Instructor account; excluded from expected-share calculations |
| 🛑 | Very low / no contribution | Very low or null Java contribution; check additional evidence |
| 🟠 | Below expected contribution | Some Java work, but under the expected share |
| ✅ | Balanced contribution | Close to expected share for the team size |
| 🌟 | High contribution | Above expected share; may indicate a strong role or imbalance |

---

## 🚩 AlertFlag (additional signals)

Flags do not change the badge: they are **extra alerts** to inspect patterns.

> Suggested order (GUI): `ENGINE → CLEANUP → AI_PASTE → RHYTHM`

### 🚀 ENGINE — “Team engine”
- **Triggers when**: `share >= expected * 2.0`  
- **Suggests**: one person contributes far above expected.  
  Review task distribution, roles, and authorship.

### 🧹 CLEANUP — “Cleanup/correction work”
- **Triggers when**:
  - high deletion ratio: `deleted / (added+deleted) >= 0.55`
  - and enough volume: `userChurn >= 200`
- **Suggests**: refactor, corrections, or restructuring.  
  Often legitimate—review continuity and context.

### 🧠 AI_PASTE — “AI/paste-like pattern”
- **Triggers when**:
  - `churnPerCommit >= repoAvgChurnPerCommit * 2.5`
  - (with `javaCommits > 0` and `repoAvgChurnPerCommit > 0`)
- **Suggests**: large bursts per commit (mass paste / AI / templates / generated code).  
  **Not proof**: ask for a code defense and understanding checks.

### ⏱️ RHYTHM — “Irregular rhythm”
- **Triggers when**:
  - user activity is concentrated late: user last commit offset > `0.85` of repo time span
  - and `userChurn >= 200`
- **Suggests**: work is end-loaded.  
  Useful for early interventions in future iterations.

---

## 🧪 Examples (how to read the UI)

### Example 1 — Balanced team members
You hover a row and see:

- Username cell: `✅ alice`
- Status bar: `✅ Balanced contribution`
- Tooltip (long): shows expected share and no flags

Interpretation:
- Alice’s churn share is within ~±20% of the expected share for the team size.
- No additional signals triggered.

---

### Example 2 — Below expected but still contributing
Row shows:

- Username cell: `🟠 bob`
- Status bar: `🟠 Below expected contribution`

Interpretation:
- Bob contributes, but the Java churn share is below the expected range.
- Next step: check whether Bob contributed mainly in non-Java files, worked through PR reviews, or had a different role.

---

### Example 3 — Team engine (possible imbalance)
Row shows:

- Username cell: `🌟 carol`
- Status bar: `🌟 High contribution | 🚀 Team “engine”`

Interpretation:
- Carol is above expected share (badge 🌟).
- Additionally, she is **≥ 2× expected** (🚀 ENGINE), which may indicate task imbalance.
- Next step: verify that roles and authored work match what the team reports.

---

### Example 4 — Cleanup-heavy work
Row shows:

- Username cell: `✅ dan`
- Status bar: `✅ Balanced contribution | 🧹 Cleanup/correction work`

Interpretation:
- Dan is within expected contribution, but with a **high deletion ratio** at non-trivial volume.
- This can be healthy refactoring—inspect diffs for structure, tests, and commit messages.

---

### Example 5 — AI/paste-like burst pattern
Row shows:

- Username cell: `🟠 eva`
- Status bar: `🟠 Below expected contribution | 🧠 AI/paste-like pattern`

Interpretation:
- Eva’s total share might be modest, but her commits have unusually high churn per commit.
- Next step: ask for a defense: explain code, reasoning, and where it came from.

---

### Example 6 — Late activity spike
Row shows:

- Username cell: `✅ fran`
- Status bar: `✅ Balanced contribution | ⏱️ Irregular rhythm`

Interpretation:
- Contribution level is okay, but it happened very late in the repo timespan.
- Next step: review planning habits and encourage earlier incremental commits.

---

## 📚 Mini glossary

- **LOC (Lines of Code)**: Number of lines in files (here, `.java`). This is a *size snapshot*, not effort.
- **Churn**: `added + deleted` lines. Used as a proxy for “how much code was edited”.
- **Share (churn share)**: A user’s churn divided by the repo churn. Used to compare relative contribution within a repo.
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

### A student shows 🛑 or 🟠 — does that mean they did nothing?
Not necessarily. They may have:
- contributed mostly in **non-Java** files
- worked through PRs with merge commits excluded from the Java stats
- split identity (different emails/logins)

Use it as a signal to ask for evidence: commits, issues, PR reviews, walkthrough, oral defense.

### Does it reliably detect AI usage?
No. It only flags patterns compatible with large pastes/AI/templates.  
Always confirm with code defense and understanding questions.

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
- Formatting/templates/generated code can inflate churn.
- Commit habits differ (many small commits vs few large ones).
- GitHub rate limits can affect refresh; offline mode helps.

---

## 🧾 Credits
- Icons: *Pixel perfect* (Flaticon) — shown in the app footer.

---

## 📜 License
MIT License — https://opensource.org/license/mit/

---

> 🧠 *This description was generated with the assistance of ChatGPT 5 and has been reviewed and validated to ensure accuracy and correctness.*
