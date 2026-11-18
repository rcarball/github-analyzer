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

package es.deusto.prog3.githubanalyzer.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import es.deusto.prog3.githubanalyzer.domain.AuthorIdentity;
import es.deusto.prog3.githubanalyzer.domain.RepositorySnapshot;

/**
 * One complete, versioned raw snapshot per URL, atomically replaced after
 * successful downloads.
 */
public final class RawDataStore {
	private static final long MAX_BYTES = 268435456;
	private static final ObjectInputFilter FILTER = ObjectInputFilter.Config
			.createFilter("maxbytes=268435456;maxdepth=48;maxrefs=4000000;maxarray=4000000;"
					+ "es.deusto.prog3.githubanalyzer.domain.*;java.util.*;java.lang.*;!*");
	private final Path directory;

	public RawDataStore(Path directory) {
		this.directory = directory;
	}

	public Path pathFor(String url) {
		return directory.resolve(AuthorIdentity.digest(url) + ".dat");
	}

	public RepositorySnapshot load(String url) {
		Path path = pathFor(url);
		if (!Files.exists(path))
			return null;
		try {
			if (Files.size(path) > MAX_BYTES)
				throw new IOException("Raw-data file is too large.");
			try (var bytes = new BufferedInputStream(Files.newInputStream(path));
					ObjectInputStream input = new ObjectInputStream(bytes)) {
				input.setObjectInputFilter(FILTER);
				Object value = input.readObject();
				if (!(value instanceof RepositorySnapshot snapshot) || !snapshot.url().equals(url))
					throw new IOException("Unexpected raw-data content or repository.");
				snapshot.activeCommits();
				return snapshot;
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Could not read raw data for " + url + ". Download it again from GitHub.",
					ex);
		}
	}

	/**
	 * Only explicit downloads may recover unreadable data; normal reads remain
	 * side-effect free.
	 */
	public RepositorySnapshot loadForUpdate(String url) {
		try {
			return load(url);
		} catch (IllegalStateException unreadable) {
			Path source = pathFor(url);
			Path backup = null;
			try {
				Path recovery = directory.resolve("recovery");
				Files.createDirectories(recovery);
				String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
						.withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.now());
				backup = recovery.resolve(AuthorIdentity.digest(url) + ".damaged-" + timestamp + "-"
						+ java.util.UUID.randomUUID() + ".dat");
				Files.copy(source, backup, StandardCopyOption.COPY_ATTRIBUTES);
				if (Files.mismatch(source, backup) != -1)
					throw new IOException("Original data changed while preserving the recovery copy.");
				System.err.println(
						"* Preserved unreadable original data at '" + backup + "'. Starting a new complete download.");
				return null;
			} catch (IOException ex) {
				if (backup != null)
					try {
						Files.deleteIfExists(backup);
					} catch (IOException ignored) {
					}
				throw new IllegalStateException(
						"Could not preserve unreadable original data. No reconstruction was started; the original file is unchanged.",
						ex);
			}
		}
	}

	public void save(RepositorySnapshot snapshot) {
		snapshot.activeCommits();
		Path temporary = null;
		try {
			Files.createDirectories(directory);
			temporary = Files.createTempFile(directory, ".raw-data-", ".tmp");
			try (ObjectOutputStream output = new ObjectOutputStream(
					new BufferedOutputStream(Files.newOutputStream(temporary)))) {
				output.writeObject(snapshot);
			}
			if (Files.size(temporary) > MAX_BYTES)
				throw new IOException("Raw-data file is too large.");
			try {
				Files.move(temporary, pathFor(snapshot.url()), StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary, pathFor(snapshot.url()), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			throw new IllegalStateException("Could not save raw repository data.", ex);
		} finally {
			if (temporary != null)
				try {
					Files.deleteIfExists(temporary);
				} catch (IOException ignored) {
				}
		}
	}
}
