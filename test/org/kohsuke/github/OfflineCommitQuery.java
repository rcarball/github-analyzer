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

package org.kohsuke.github;

import java.util.List;
import java.util.Map;

/**
 * Test-only adapter for package-private API constructors; never opens an HTTP
 * connection.
 */
public final class OfflineCommitQuery extends GHCommitQueryBuilder {
	private final Map<String, List<GHCommit>> branches;
	private final java.util.function.Consumer<String> onFrom;
	private final int maxPageSize;
	private String ref;

	public OfflineCommitQuery(Map<String, List<GHCommit>> branches) {
		this(branches, ignored -> {
		});
	}

	public OfflineCommitQuery(Map<String, List<GHCommit>> branches, java.util.function.Consumer<String> onFrom) {
		this(branches, onFrom, Integer.MAX_VALUE);
	}

	public OfflineCommitQuery(Map<String, List<GHCommit>> branches, java.util.function.Consumer<String> onFrom,
			int maxPageSize) {
		super(new GHRepository() {
			@Override
			GitHub root() {
				return GitHub.offline();
			}
		});
		this.branches = branches;
		this.onFrom = onFrom;
		this.maxPageSize = maxPageSize;
	}

	@Override
	public GHCommitQueryBuilder from(String value) {
		ref = value;
		onFrom.accept(value);
		return this;
	}

	@Override
	public PagedIterable<GHCommit> list() {
		List<GHCommit> commits = branches.getOrDefault(ref, List.of());
		return new PagedIterable<>() {
			@Override
			public PagedIterator<GHCommit> _iterator(int pageSize) {
				int size = Math.max(1, Math.min(pageSize, maxPageSize));
				List<GHCommit[]> pages = new java.util.ArrayList<>();
				for (int first = 0; first < commits.size(); first += size)
					pages.add(commits.subList(first, Math.min(first + size, commits.size())).toArray(GHCommit[]::new));
				return new PagedIterator<>(pages.iterator(), ignored -> {
				});
			}
		};
	}
}
