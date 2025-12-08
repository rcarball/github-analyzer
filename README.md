# 📊 GitHub Analyzer (Teaching-Oriented)

Java (Swing) application to analyze activity in GitHub repositories for **team projects** (e.g., students). It supports **public** and **private** repositories (if the token has access).

> 🧭 Teaching focus: these metrics are designed to approximate **hands-on programming practice** (especially in Java) in a learning context.  
> Therefore, some numbers may **not match** GitHub *Insights → Contributors* (see “📐 Metrics meaning”, “❓ FAQ” and “🔒 Privacy & teaching ethics”).

---

## ✨ What the app does

- 🌳 Shows a **tree** with the analyzed repositories.
- 🧾 When selecting a repo, displays **general repository metrics**.
- 👥 Displays a **per-person table** with commit/line metrics (focused on `.java`).
- 🧩 Includes a summary of **file types** found in the repository.
- 🔄 Allows refreshing data from GitHub or working **offline** using a cached snapshot.

---

## 🧑‍🏫 Teaching rationale (why these metrics)

In teams with limited Git experience, little testing/documentation, and minimal refactoring/design practices, it is often useful to measure:

- **Java commits (non-merge)** → frequency of real work in code.
- **Java churn (added + deleted)** → volume of effective edits.
- **Net Java (added − deleted)** → net growth (optional, useful to interpret “cleanup/correction” work).

✅ These metrics are **indicators**: they help guide review and spot unusual patterns, but they do **not** replace qualitative assessment.

---

## 🚀 How to run

### Requirements
- ☕ Java 17+ (recommended)
- 🗂️ Libraries are included in the `lib/` folder (no Maven/Gradle)
- 🔑 GitHub token (recommended; required for private repositories)

### Main class
- `es.deusto.prog3.githubanalyzer.Main`

### Running (no Maven/Gradle)
Depends on your IDE, but typically:

- Import as a **Java Project**
- Add `lib/` to the project **Build Path**
- Run the `Main` class

---

## ⚙️ Configuration

The application uses two main files:

### 1) `resources/config.properties`

```properties
github.user=_USERNAME_                         # Username (informational)
github.token=_TOKEN_                           # Access token (recommended for private repos)
update.from.github=_<yes|no>_                  # yes: online refresh | no: offline mode (uses stats.dat)
repositories.file=resources/repositories.txt   # Repository list
stats.file=resources/stats.dat                 # Binary cache of last refresh

# (Optional) To exclude the "teacher" account from some GUI comparisons
teacher.user=_TEACHER_USERNAME_
teacher.email=_TEACHER_EMAIL_
```

### 2) `resources/repositories.txt`

One repository per line:

```txt
https://github.com/OWNER/REPO1
https://github.com/OWNER/REPO2
```

---

## 🔐 GitHub token

For private repositories and to reduce limitations when refreshing frequently, use a token with **read** permissions for the repositories.

> 🧠 Practical advice: if you refresh many times in a row (e.g., ~20 repos), GitHub may temporarily rate-limit requests.  
> In that case, use offline mode (`update.from.github=no`) and refresh again later.

---

## 📐 Metrics meaning

### 🧾 Repository-level metrics

- **🧱 Total commits (unique)**  
  Number of **unique commits** detected (deduplicated by SHA) across the analyzed history, considering **all known branches**.  
  ✅ Useful as a global activity indicator.  
  ⚠️ This is not the same as “Java commits per person” because that metric uses a different definition (see table).

- **📅 Creation date**  
  Repository creation date on GitHub.

- **⏱️ First commit / Last commit**  
  Earliest and latest commit detected in the analyzed history.  
  📌 Useful to estimate the real working period and detect “last-minute” work.

- **📄 Total lines of code**  
  Number of lines currently present in **`.java`** files (content snapshot).  
  📌 This is a “final-state photo”, not a direct measure of effort.

- **🔁 Java churn (added + deleted)**  
  Total **added + deleted** lines in `.java` (from analyzed commits, excluding merge commits).  
  ✅ Good proxy for effective editing work.  
  ⚠️ Can be inflated by mass pastes, formatting, or auto-generated code.

- **🔗 External references**  
  Number of occurrences in `.java` of these patterns: `IAG` or `FUENTE-EXTERNA`.  
  📌 A signal to identify external references or generative-AI related code.

---

### 👤 Per-person table (collaborators/authors)

> Identity: when possible, the app uses the GitHub **login**; otherwise it falls back to commit author information.  
> The app also attempts to merge identities that likely belong to the same person (e.g., login + `noreply`, same email local-part, or anchored-name matching).

- **✅ Java commits**  
  Number of commits (**excluding merges**) that modify at least one `.java` file.  
  🎯 Interpreted here as “real coding commits”.

- **➕ Java added / ➖ Java deleted**  
  Added/deleted lines in `.java` (summed over non-merge commits).

- **🔁 Java churn**  
  `added + deleted` in `.java`.  
  ✅ Used to assess relative contribution while reducing bias (counting only added lines penalizes those who fix by deleting).

- **📊 % Java churn**  
  Share of total repository Java churn attributed to a person.  
  📌 Useful to compare contributions within the team.

- **🧩 Java files**  
  Number of distinct `.java` files modified.  
  📌 Helps distinguish “wide intervention” vs “localized work”.

- **📅 First / Last commit (user)**  
  First/last non-merge commit date considered for the person.  
  📌 Useful for identifying inactivity windows and end-loaded activity.

---

## 🧑‍🏫 Quick interpretation for instructors (practical)

> Goal: guide review and detect cases worth checking—not “automatic grading”.

- ✅ **Balanced contribution**: several members with similar % churn and distributed Java commits → work is usually shared.
- ⚠️ **One “engine” in the team**: one person with >50–60% churn and many Java commits → likely carried the workload; review task distribution and authorship.
- ⚠️ **Minimal contribution**: churn near zero or almost no Java commits → review history, team communication, and additional evidence (issues, commit messages, oral defense).
- 🧠 **Typical AI/paste pattern**: very high churn with very few Java commits (e.g., 2 commits and 2000 lines) → request a defense: explain code, trace execution, comprehension questions.
- 🔁 **Correction/cleanup work**: high deleted and high churn but low net → may be cleanup; verify sustained Java commits and distributed changes.
- ⏱️ **Irregular rhythm**: most churn concentrated in the last days → often indicates accumulation and lower understanding; useful to plan the individual lab exam.

---

## ❓ FAQ

### Why don’t the numbers match GitHub *Insights → Contributors*?
GitHub’s Insights uses its own heuristics and may attribute activity based on merge strategies, default-branch history, UI grouping, and additional signals.  
This app intentionally uses a **teaching-focused definition**:
- Per-person “Java commits” count only **non-merge commits touching `.java`**
- “Java churn” counts only `.java` **added + deleted** from **non-merge commits**
- Repository “unique commits” are deduplicated by **SHA** across analyzed branches

So the goal is **consistency for teaching interpretation**, not matching the GitHub UI.

### A student appears with very low contribution (⛔/⚠️). Does it mean they did nothing?
Not necessarily. Common reasons:
- They contributed mostly to **non-Java** files (docs, configs, assets)
- They worked via merges/PRs that get represented as **merge commits** (excluded for “real work” line metrics)
- Their identity is split across logins/emails and needs merging (see next question)

Use this as an indicator to ask for evidence: commit messages, issues, discussions, or an oral explanation.

### Why does the app “merge” identities? Is it safe?
In student projects it is common to have:
- GitHub `noreply` addresses
- Different emails across machines
- Different display names vs logins  
The app merges identities when there is strong evidence they are the same person (e.g., same email, same email local-part, anchored-name matches).  
This reduces “duplicate rows” for one student.

### What about AI-generated code? Does the app detect it?
The app does **not** claim to detect AI usage reliably.  
It can highlight **patterns** (e.g., high churn with very few commits) that are common in copy/paste or AI-assisted bursts.  
Always confirm with a defense: explanation, tracing, and comprehension questions.

### Can churn be inflated even with honest work?
Yes. Churn increases with:
- formatting or auto-formatting
- large refactors (even if correct)
- generated code or templates  
Treat churn as a proxy, not a direct measure of skill.

### The refresh sometimes fails or seems slow—what can I do?
If you refresh many repositories repeatedly, GitHub may temporarily rate-limit requests.
- Use offline mode (`update.from.github=no`) to work from `stats.dat`
- Refresh again later
- Avoid repeated “refresh” cycles in short intervals

---

## 🔒 Privacy & teaching ethics

This tool is designed for **educational support**, and its outputs should be handled responsibly.

**Recommended principles:**
- ✅ **Transparency**: inform students that repository activity is analyzed and explain what is measured (and what is not).
- ✅ **Proportionality**: use the metrics to *guide* review and interviews, not as an automatic grade.
- ✅ **Context first**: interpret outliers with context (team roles, setup tasks, merges, non-Java contributions).
- ✅ **Right to explain**: if metrics suggest an anomaly, allow students to provide evidence (issues, planning, oral defense, code walkthrough).
- ✅ **Minimize exposure**: avoid publicly sharing per-student metrics or screenshots with identifiable information.
- ✅ **Data retention**: keep cached data (`stats.dat`) only as long as needed for assessment and feedback.

> 🧠 The goal is to promote learning and fairness, not surveillance.  
> Treat metrics as *signals*, not verdicts.

---

## 🧠 Limitations (to avoid misunderstandings)

- These metrics do not directly measure quality (correctness, design, style).
- Formatting changes or large pastes can inflate churn.
- Commit habits vary widely (many small commits vs few large commits).
- GitHub’s UI and heuristics may attribute contributions differently.
- If GitHub rate-limits requests, online refresh may slow down or fail; offline mode can help.


## 🧾 Credits
- Icons: *Pixel perfect* (Flaticon) — shown in the application.

---

## 📜 License

This project is released under the **MIT License**.  
More information: https://opensource.org/license/mit/