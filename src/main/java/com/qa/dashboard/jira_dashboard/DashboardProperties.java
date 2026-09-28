package com.qa.dashboard.jira_dashboard;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {

    private List<Domain> domains = new ArrayList<>();

    public List<Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<Domain> domains) {
        this.domains = domains;
    }

    public static class Domain {
        private String id;
        private String name;
        private List<Project> projects = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<Project> getProjects() {
            return projects;
        }

        public void setProjects(List<Project> projects) {
            this.projects = projects;
        }
    }

    public static class Project {
        private String id;
        private String name;
        private String jiraProjectId;
        private Integer boardId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getJiraProjectId() {
            return jiraProjectId;
        }

        public void setJiraProjectId(String jiraProjectId) {
            this.jiraProjectId = jiraProjectId;
        }

        public Integer getBoardId() {
            return boardId;
        }

        public void setBoardId(Integer boardId) {
            this.boardId = boardId;
        }
    }
}