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
import java.util.Objects;

public class UserStats implements Serializable, Comparable<UserStats> {

	private static final long serialVersionUID = 1L;
	private String username, email;
	private int commits, javaFiles;
	private int added, deleted, changed;
	private long firstCommit, lastCommit;
	
	public UserStats(String username, String email, int commits, 
			         int javaFiles, int added, int deleted, 
			         int changed, long firstCommit, long lastCommit) {		
		this.username = username;
		this.email = email;
		this.commits = commits;
		this.javaFiles = javaFiles;
		this.added = added;
		this.deleted = deleted;
		this.changed = changed;
		this.firstCommit = firstCommit;
		this.lastCommit = lastCommit;
	}

	public String getEmail() {
        return email;
	}
	
	public String getUsername() {
		return username;
	}

	public int getCommits() {
		return commits;
	}

	public int getJavaFiles() {
		return javaFiles;
	}

	public int getAdded() {
		return added;
	}
	
	public int getDeleted() {
		return deleted;
	}
	
	public int getChanged() {
        return changed;
	}

	public long getFirstCommit() {
		return firstCommit;
	}

	public long getLastCommit() {
		return lastCommit;
	}

	public int getChurn() {
		return added + deleted;
	}
	
	public int getNetLines() {
		return added - deleted;
	}

	@Override
	public int hashCode() {
		String e = (email == null) ? "" : email.toLowerCase();
		String u = (username == null) ? "" : username.toLowerCase();
		return Objects.hash(e, u);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		UserStats other = (UserStats) obj;
		
		String thisEmail = (this.email == null) ? "" : this.email.toLowerCase();
		String otherEmail = (other.email == null) ? "" : other.email.toLowerCase();
		String thisUser = (this.username == null) ? "" : this.username.toLowerCase();
		String otherUser = (other.username == null) ? "" : other.username.toLowerCase();
		
		return thisEmail.equals(otherEmail) && thisUser.equals(otherUser);
	}
	
	@Override
	public String toString() {
		return "UserStats [username=" + username + ", email=" + email + ", commits=" + commits + ", javaFiles="
				+ javaFiles + ", added=" + added + ", deleted=" + deleted + ", changed=" + changed + ", firstCommit="
				+ firstCommit + ", lastCommit=" + lastCommit + "]";
	}

	
	@Override
	public int compareTo(UserStats o) {
	    if (o == null) return -1;

	    int thisChurn = this.added + this.deleted;
	    int otherChurn = o.added + o.deleted;

	    // 1) Churn
	    int c = Integer.compare(otherChurn, thisChurn);
	    if (c != 0) return c;

	    // 2) Commits
	    c = Integer.compare(o.commits, this.commits);
	    if (c != 0) return c;

	    // 3) Files
	    c = Integer.compare(o.javaFiles, this.javaFiles);
	    if (c != 0) return c;

	    // 4) Last Commit
	    c = Long.compare(o.lastCommit, this.lastCommit);
	    if (c != 0) return c;

	    // 5) Username
	    String u1 = (this.username == null) ? "" : this.username;
	    String u2 = (o.username == null) ? "" : o.username;
	    return u1.compareToIgnoreCase(u2);
	}
}