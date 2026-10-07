package com.qa.dashboard.jira_dashboard;

import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class DashboardController {

    private final JiraService jiraService;
    private final URI dataFeederUri;

    DashboardController(
            JiraService jiraService,
            @Value("${dashboard.datafeeder-url:http://localhost:8090/}") URI dataFeederUri) {
        this.jiraService = jiraService;
        this.dataFeederUri = dataFeederUri;
    }

    @GetMapping("/")
    public ResponseEntity<Void> home() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/index.html"))
                .build();
    }

    @GetMapping({"/datafeeder", "/datafeeder/", "/datafeeder/index.html"})
    public ResponseEntity<Void> dataFeeder() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(dataFeederUri)
                .build();
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