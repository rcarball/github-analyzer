package org.kohsuke.github;
import java.util.List;
/** Test-only pagination without an HTTP connector. */
public final class OfflinePages {
    private OfflinePages() { }
    public static <T> PagedIterable<T> of(T[] values) {
        return new PagedIterable<>() {
            @Override public PagedIterator<T> _iterator(int pageSize) {
                List<T[]> pages = java.util.Collections.singletonList(values);
                return new PagedIterator<>(pages.iterator(), ignored -> { });
            }
        };
    }
}
