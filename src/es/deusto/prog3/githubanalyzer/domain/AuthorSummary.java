package es.deusto.prog3.githubanalyzer.domain;

/** Fresh discovery data for the editor; never loaded from a statistics cache. */
public record AuthorSummary(AuthorIdentity identity, int javaCommits) { }
