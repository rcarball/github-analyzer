package es.deusto.prog3.githubanalyzer.domain;

import java.util.Objects;

public class SimpleGitUser {
    private String name;
    private String email;

    public SimpleGitUser(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

	@Override
	public int hashCode() {
		return Objects.hash(email, name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SimpleGitUser other = (SimpleGitUser) obj;
		return Objects.equals(email, other.email) && Objects.equals(name, other.name);
	}

	@Override
	public String toString() {
		return "SimpleGitUser [name=" + name + ", email=" + email + "]";
	}
}
