// IAG (herramientas: ChatGPT (OpenAI), Claude Code (Anthropic), Codex (OpenAI))
// SIN CAMBIOS

/*
 * AI ASSISTANCE AND REVIEW DISCLOSURE
 *
 * The initial version of this codebase was developed in 2024 with partial
 * assistance from ChatGPT (OpenAI).
 *
 * From July to September 2026, the codebase was reviewed and audited using
 * Claude Code (Anthropic) and Codex (OpenAI).
 *
 * The resulting version was reviewed, tested, and refined to identify and
 * correct issues within the scope of the performed verification activities.
 */

package es.deusto.prog3.githubanalyzer.domain;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Complete immutable download. Contains no API objects or person assignments.
 */
public record RepositorySnapshot(int version, String url, String name, long capturedAt, long createdAt,
		long lastPushTime, boolean publicRepository, String defaultBranch, Map<String, String> branchHeads,
		List<Commit> commits, List<SourceFile> sourceFiles) implements Serializable {
	private static final long serialVersionUID = 1L;
	public static final int CURRENT_VERSION = 1;

	public RepositorySnapshot {
		if (version != CURRENT_VERSION)
			throw new IllegalArgumentException("Unsupported raw-data version: " + version);
		if (url == null || url.isBlank() || name == null || capturedAt <= 0)
			throw new IllegalArgumentException("Incomplete raw repository metadata.");
		defaultBranch = Objects.requireNonNull(defaultBranch);
		branchHeads = Map.copyOf(branchHeads);
		commits = List.copyOf(commits);
		sourceFiles = List.copyOf(sourceFiles);
		var shas = new HashSet<String>();
		for (Commit commit : commits)
			if (!shas.add(commit.sha()))
				throw new IllegalArgumentException("Duplicate commit SHA in raw data.");
		var paths = new HashSet<String>();
		for (SourceFile file : sourceFiles)
			if (!paths.add(file.path()))
				throw new IllegalArgumentException("Duplicate snapshot file path.");
	}

	/**
	 * Reachability is derived from the latest branch heads; old original commits
	 * are retained.
	 */
	public List<Commit> activeCommits() {
		if (branchHeads.isEmpty())
			return List.of();
		Map<String, Commit> bySha = new java.util.HashMap<>();
		commits.forEach(commit -> bySha.put(commit.sha(), commit));
		var reachable = new HashSet<String>();
		var pending = new java.util.ArrayDeque<>(branchHeads.values());
		while (!pending.isEmpty()) {
			String sha = pending.removeFirst();
			if (!reachable.add(sha))
				continue;
			Commit commit = bySha.get(sha);
			if (commit == null)
				throw new IllegalStateException("Incomplete raw history: " + sha);
			pending.addAll(commit.parents());
		}
		return commits.stream().filter(commit -> reachable.contains(commit.sha())).toList();
	}

	public record ChangedFile(String path, String previousPath, int added, int deleted) implements Serializable {
		private static final long serialVersionUID = 1L;

		public ChangedFile {
			if (path == null || path.isBlank() || added < 0 || deleted < 0)
				throw new IllegalArgumentException("Incomplete commit file.");
			previousPath = previousPath == null ? "" : previousPath;
		}

		public boolean isJava() {
			return path.toLowerCase(Locale.ROOT).endsWith(".java");
		}
	}

	public record Commit(String sha, AuthorIdentity author, long timestamp, List<String> parents,
			List<ChangedFile> files, Long authorTimestamp) implements Serializable {
		private static final long serialVersionUID = 1L;

		/**
		 * Old downloads lack an author date; retain their recorded commit date as
		 * fallback.
		 */
		public Commit(String sha, AuthorIdentity author, long timestamp, List<String> parents,
				List<ChangedFile> files) {
			this(sha, author, timestamp, parents, files, null);
		}

		public Commit {
			if (sha == null || sha.isBlank())
				throw new IllegalArgumentException("Missing commit SHA.");
			Objects.requireNonNull(author);
			parents = List.copyOf(parents);
			files = List.copyOf(files);
		}

		public boolean isMerge() {
			return parents.size() > 1;
		}

		public long activityTimestamp() {
			return authorTimestamp == null || authorTimestamp == -1 ? timestamp : authorTimestamp;
		}
	}

	/**
	 * Current default-branch Java text is retained for LOC and marker calculations.
	 */
	public record SourceFile(String path, String javaContent, String blobSha) implements Serializable {
		private static final long serialVersionUID = 1L;

		public SourceFile(String path, String javaContent) {
			this(path, javaContent, "");
		}

		public SourceFile {
			blobSha = blobSha == null ? "" : blobSha;
			if (path == null || path.isBlank())
				throw new IllegalArgumentException("Missing source file path.");
			if (path.toLowerCase(Locale.ROOT).endsWith(".java") && javaContent == null)
				throw new IllegalArgumentException("Incomplete Java source snapshot.");
		}
	}
}
