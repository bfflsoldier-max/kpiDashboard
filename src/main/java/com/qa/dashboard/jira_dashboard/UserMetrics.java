package com.qa.dashboard.jira_dashboard;

public class UserMetrics {

    private String name;
    private int resolved;
    private int created;

    public UserMetrics(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    public int getResolved() { return resolved; }
    public void setResolved(int resolved) { this.resolved = resolved; }

    public int getCreated() { return created; }
    public void setCreated(int created) { this.created = created; }
}
