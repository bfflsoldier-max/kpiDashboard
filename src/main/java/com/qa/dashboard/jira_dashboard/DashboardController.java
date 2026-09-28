package com.qa.dashboard.jira_dashboard;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class DashboardController {

    private final JiraService jiraService;

    DashboardController(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    @GetMapping("/")
    public String home() {
        return "Jira dashboard backend is running. Use /api/domains to inspect configured domains.";
    }

    @GetMapping("/api/domains")
    public List<DashboardProperties.Domain> getDomains() {
        return jiraService.getDomains();
    }

    @GetMapping("/api/domains/{domainId}/projects")
    public List<DashboardProperties.Project> getProjects(@PathVariable String domainId) {
        return jiraService.getProjects(domainId);
    }

    @GetMapping("/api/projects/{projectId}/boards")
    public JsonNode getProjectBoards(@PathVariable String projectId) throws Exception {
        return jiraService.getProjectBoards(projectId);
    }

    @GetMapping("/api/projects/{projectId}/sprints")
    public List<JsonNode> getProjectSprints(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "active,closed") String state) throws Exception {
        return jiraService.getProjectSprints(projectId, state);
    }
}