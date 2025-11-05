package es.deusto.prog3.githubanalyzer.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RepoStats implements Serializable, Comparable<RepoStats> {

	private static final long serialVersionUID = 1L;

	private String url;
	private String name;
	private List<UserStats> userStats = new ArrayList<>();
	private long creationDate;
	private long firstCommit = -1;
	private long lastCommit = -1;
	private int branches;
	private int commits;
	private int codeLines;
	private int linesAdded, linesDeleted, linesChanged;
	private int externalReferences;
	private boolean isPublic;
	private long lastPushTime;
	
	private Map<String, Integer> fileTypeMap = new HashMap<>();
	
	public int getBranches() {
		return branches;
	}

	public void setBranches(int branches) {
		this.branches = branches;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<UserStats> getUserStats() {
		return userStats;
	}

	public void setUserStats(List<UserStats> userStats) {
		this.userStats = userStats;
	}
	
	public void addUserStats(UserStats user) {
		if (user != null && !userStats.contains(user)) {
			userStats.add(user);
			
			linesAdded += user.getAdded();
			commits += user.getCommits();
			
			if (lastCommit == -1 && user.getLastCommit() != -1) {
				lastCommit = user.getLastCommit();
			} else if (lastCommit != -1 && user.getLastCommit() != -1) {
				lastCommit = Math.max(lastCommit, user.getLastCommit());
			}			
			
			if (firstCommit == -1 && user.getFirstCommit() != -1) {
				firstCommit = user.getFirstCommit();
            } else if (firstCommit != -1 && user.getFirstCommit() != -1) {
                firstCommit = Math.min(firstCommit, user.getFirstCommit());
            }
		}
	}

	public long getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(long creationDate) {
		this.creationDate = creationDate;
	}

	public long getFirstCommit() {
		return firstCommit;
	}

	public void setFirstCommit(long firstCommit) {
		this.firstCommit = firstCommit;
	}

	public long getLastCommit() {
		return lastCommit;
	}

	public void setLastCommit(long lastCommit) {
		this.lastCommit = lastCommit;
	}

	public int getCommits() {
		return commits;
	}

	public void setCommits(int commits) {
		this.commits = commits;
	}

	public int getCodeLines() {
		return codeLines;
	}

	public void setCodeLines(int codeLines) {
		this.codeLines = codeLines;
	}

	public int getExternalReferences() {
		return externalReferences;
	}

	public void setExternalReferences(int externalReferences) {
		this.externalReferences = externalReferences;
	}

	public int getLinesAdded() {
		return linesAdded;
	}

	public void setLinesAdded(int linesAdded) {
		this.linesAdded = linesAdded;
	}

	public Map<String, Integer> getFileTypeMap() {
		return fileTypeMap;
	}

	public void setFileTypeMap(Map<String, Integer> fileTypeMap) {
		this.fileTypeMap = fileTypeMap;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}
	
	public boolean isPublic() {
		return isPublic;
	}
	
	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	@Override
	public int hashCode() {
		return Objects.hash(url);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RepoStats other = (RepoStats) obj;
		return Objects.equals(url, other.url);
	}

	@Override
	public int compareTo(RepoStats o) {
		return this.name.compareTo(o.name);
	}

	public long getLastPushTime() {
		return lastPushTime;
	}

	public void setLastPushTime(long lastPushTime) {
		this.lastPushTime = lastPushTime;
	}

	public int getLinesDeleted() {
		return linesDeleted;
	}

	public void setLinesDeleted(int linesDeleted) {
		this.linesDeleted = linesDeleted;
	}

	public int getLinesChanged() {
		return linesChanged;
	}

	public void setLinesChanged(int linesChanged) {
		this.linesChanged = linesChanged;
	}
}