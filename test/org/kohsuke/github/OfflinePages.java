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

/** Test-only pagination without an HTTP connector. */
public final class OfflinePages {
	private OfflinePages() {
	}

	public static <T> PagedIterable<T> of(T[] values) {
		return of(values, Integer.MAX_VALUE);
	}

	public static <T> PagedIterable<T> of(T[] values, int maxPageSize) {
		return new PagedIterable<>() {
			@Override
			public PagedIterator<T> _iterator(int pageSize) {
				int size = Math.max(1, Math.min(pageSize, maxPageSize));
				List<T[]> pages = new java.util.ArrayList<>();
				for (int first = 0; first < values.length; first += size)
					pages.add(java.util.Arrays.copyOfRange(values, first, Math.min(first + size, values.length)));
				return new PagedIterator<>(pages.iterator(), ignored -> {
				});
			}
		};
	}
}
