package com.qa.dashboard.jira_dashboard;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class JiraService {

    private final JiraProperties jiraProperties;
    private final DashboardProperties dashboardProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JiraService(JiraProperties jiraProperties, DashboardProperties dashboardProperties) {
        this.jiraProperties = jiraProperties;
        this.dashboardProperties = dashboardProperties;
    }

    public List<DashboardProperties.Domain> getDomains() {
        return dashboardProperties.getDomains();
    }

    public List<DashboardProperties.Project> getProjects(String domainId) {
        return dashboardProperties.getDomains().stream()
                .filter(domain -> domainId.equals(domain.getId()))
                .findFirst()
                .map(DashboardProperties.Domain::getProjects)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Domain is not configured"));
    }

    public JsonNode getProjectBoards(String projectId) throws Exception {
        DashboardProperties.Project project = requireProject(projectId);
        JsonNode response = jiraGet("rest/agile/1.0/board", "projectKeyOrId", project.getJiraProjectId());
        return response.path("values");
    }

    public List<JsonNode> getProjectSprints(String projectId, String state) throws Exception {
        DashboardProperties.Project project = requireProject(projectId);
        int boardId = project.getBoardId() != null ? project.getBoardId() : findLatestBoardId(project);
        if (boardId < 0) {
            return List.of();
        }

        List<JsonNode> sprints = new ArrayList<>();
        int startAt = 0;
        boolean lastPage = false;
        while (!lastPage) {
            JsonNode response = jiraGet(
                    "rest/agile/1.0/board/" + boardId + "/sprint",
                    "state", state,
                    "maxResults", "50",
                    "startAt", String.valueOf(startAt));
            response.path("values").forEach(sprints::add);
            lastPage = response.path("isLast").asBoolean(true);
            startAt += 50;
        }
        return sprints;
    }

    private int findLatestBoardId(DashboardProperties.Project project) throws Exception {
        JsonNode boards = getProjectBoards(project.getId());
        int selectedBoardId = -1;
        String latestActivation = "";
        for (JsonNode board : boards) {
            int boardId = board.path("id").asInt(-1);
            if (boardId < 0) {
                continue;
            }
            JsonNode activeSprints = jiraGet(
                    "rest/agile/1.0/board/" + boardId + "/sprint",
                    "state", "active");
            for (JsonNode sprint : activeSprints.path("values")) {
                String activation = sprint.path("activatedDate").asText("");
                if (!activation.isEmpty() && activation.compareTo(latestActivation) > 0) {
                    latestActivation = activation;
                    selectedBoardId = boardId;
                }
            }
        }
        if (selectedBoardId < 0 && !boards.isEmpty()) {
            selectedBoardId = boards.get(0).path("id").asInt(-1);
        }
        return selectedBoardId;
    }

    private DashboardProperties.Project requireProject(String projectId) {
        return dashboardProperties.getDomains().stream()
                .flatMap(domain -> domain.getProjects().stream())
                .filter(project -> projectId.equals(project.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project is not configured"));
    }

    private JsonNode jiraGet(String path, String... queryParameters) throws Exception {
        String baseUrl = jiraProperties.getUrl();
        String token = jiraProperties.getToken();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Jira URL is not configured");
        }
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Jira credentials are not configured");
        }

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl.replaceAll("/+$", "") + "/" + path);
        for (int index = 0; index < queryParameters.length; index += 2) {
            builder.queryParam(queryParameters[index], queryParameters[index + 1]);
        }
        URI uri = builder.build().encode().toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Accept", "application/json");
        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = response.getBody();
        if (body == null || body.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Jira returned an empty response");
        }
        return objectMapper.readTree(body);
    }
}