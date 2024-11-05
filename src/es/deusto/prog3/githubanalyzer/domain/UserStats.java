package es.deusto.prog3.githubanalyzer.domain;

import java.io.Serializable;
import java.util.Objects;

public class UserStats implements Serializable, Comparable<UserStats> {

	private static final long serialVersionUID = 1L;
	private String username, email;
	private int commits, javaFiles;
	private int added, deleted, changed;
	private long firstCommit, lastCommit;
	
	public UserStats(String username, String email, int commits, int javaFiles, int added, int deleted, int changed, long firstCommit, long lastCommit) {		
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

	@Override
	public int hashCode() {
		return Objects.hash(username);
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
		return Objects.equals(username, other.username);
	}
	
	@Override
	public String toString() {
		return "UserStats [username=" + username + ", email=" + email + ", commits=" + commits + ", javaFiles="
				+ javaFiles + ", added=" + added + ", deleted=" + deleted + ", changed=" + changed + ", firstCommit="
				+ firstCommit + ", lastCommit=" + lastCommit + "]";
	}

	@Override
	//Criterio de ordenación por defecto de UserStats
	//- 1.º Mayor núm. de líneas
	//- 2.º Mayor núm. de ficheros
	//- 3.º Mayor núm. de commits
	//- 4.º Username alfabéticamente
	public int compareTo(UserStats o) {
		if (added != o.added) {
			return Integer.compare(o.added, added);
		} else if (javaFiles != o.javaFiles) {
			return Integer.compare(o.javaFiles, javaFiles);
		} else if (commits != o.commits) {
			return Integer.compare(o.commits, commits);
		} else {
			return username.compareTo(o.username);
		}
	}
}