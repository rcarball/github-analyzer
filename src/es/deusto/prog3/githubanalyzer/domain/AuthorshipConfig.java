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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Explicit per-repository assignments. A missing assignment means a separate
 * person.
 */
public final class AuthorshipConfig {
	public enum Role {
		MEMBER, TEACHER, EXCLUDED
	}

	public record Person(String id, String name, Role role, String email, String login) {
		public Person(String id, String name, Role role) {
			this(id, name, role, "", "");
		}

		public Person {
			if (id == null || id.isBlank() || name == null || name.isBlank() || role == null)
				throw new IllegalArgumentException("Every person needs an ID, a name and a role.");
			name = name.trim();
			email = email == null ? "" : email.trim();
			login = login == null ? "" : login.trim();
		}

		public String emailFor(List<AuthorIdentity> identities) {
			return preferredValue(email, identities, AuthorIdentity::email);
		}

		public String loginFor(List<AuthorIdentity> identities) {
			return preferredValue(login, identities, AuthorIdentity::login);
		}

		private static String preferredValue(String preferred, List<AuthorIdentity> identities,
				Function<AuthorIdentity, String> field) {
			if (!preferred.isEmpty() && identities.stream().map(field).anyMatch(preferred::equals))
				return preferred;
			// Match the existing deterministic email fallback; a split must not display
			// another person's metadata.
			return identities.stream().sorted(Comparator.comparing(AuthorIdentity::key)).map(field)
					.filter(value -> !value.isEmpty()).findFirst().orElse("");
		}
	}

	private final Map<String, Person> people = new LinkedHashMap<>();
	private final Map<String, String> assignments = new LinkedHashMap<>();

	public AuthorshipConfig copy() {
		AuthorshipConfig copy = new AuthorshipConfig();
		copy.people.putAll(people);
		copy.assignments.putAll(assignments);
		return copy;
	}

	public Map<String, Person> people() {
		return Map.copyOf(people);
	}

	public Map<String, String> assignments() {
		return Map.copyOf(assignments);
	}

	public void putPerson(Person person) {
		people.put(person.id(), person);
	}

	public void assign(String identityKey, String personId) {
		if (identityKey == null || identityKey.isBlank() || !people.containsKey(personId))
			throw new IllegalArgumentException("An identity must reference an existing person.");
		assignments.put(identityKey, personId);
	}

	public Person personFor(AuthorIdentity identity) {
		return people.get(assignments.get(identity.key()));
	}

	/**
	 * Initialize a discovered identity without changing any existing assignment or
	 * role.
	 */
	public Person discover(AuthorIdentity identity, Role defaultRole) {
		Person existing = personFor(identity);
		if (existing != null)
			return existing;
		// Prefix reserves a different namespace from user-created UUID person IDs.
		String id = "author-" + identity.key();
		Person person = people.get(id);
		if (person == null) {
			person = new Person(id, identity.displayName(), defaultRole);
			putPerson(person);
		}
		assign(identity.key(), person.id());
		return person;
	}
}
