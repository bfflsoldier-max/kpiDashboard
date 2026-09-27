package com.qa.dashboard.jira_dashboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jira")
public class JiraProperties {

  private String url;
  private String token;
  private Sprint sprint = new Sprint();
  private Cache cache = new Cache();

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Sprint getSprint() {
    return sprint;
  }

  public void setSprint(Sprint sprint) {
    this.sprint = sprint;
  }

  public Cache getCache() {
    return cache;
  }

  public void setCache(Cache cache) {
    this.cache = cache;
  }

  public static class Sprint {
    private List<String> projects = new ArrayList<>();

    public List<String> getProjects() {
      return projects;
    }

    public void setProjects(List<String> projects) {
      this.projects = projects;
    }
  }

  public static class Cache {
    private long sprintInfoMs = 60000;
    private long ticketsMs = 60000;
    private long userSprintMs = 60000;
    private long issueCountMs = 180000;

    public long getSprintInfoMs() {
      return sprintInfoMs;
    }

    public void setSprintInfoMs(long sprintInfoMs) {
      this.sprintInfoMs = sprintInfoMs;
    }

    public long getTicketsMs() {
      return ticketsMs;
    }

    public void setTicketsMs(long ticketsMs) {
      this.ticketsMs = ticketsMs;
    }

    public long getUserSprintMs() {
      return userSprintMs;
    }

    public void setUserSprintMs(long userSprintMs) {
      this.userSprintMs = userSprintMs;
    }

    public long getIssueCountMs() {
      return issueCountMs;
    }

    public void setIssueCountMs(long issueCountMs) {
      this.issueCountMs = issueCountMs;
    }
  }
}