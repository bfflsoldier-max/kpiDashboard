package com.qa.dashboard.jira_dashboard;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

@RestController
public class DashboardController {

    private final JiraService jiraService;

    DashboardController(JiraService jiraService) {
        this.jiraService = jiraService;
    }

    @GetMapping("/")
    public String home() {
        return "App is running. Use /jira or /metrics";
    }

    @GetMapping("/jira")
    public String getJiraData() {
        return jiraService.getJiraTickets();
    }

    @GetMapping("/sprintInfo")
    public Map<String, Map<String, String>> getSprint() {
        return jiraService.getSprintInfo();
    }

    @GetMapping("/arcxpTeamSummary")
    public TeamSprintSummary getArcXpTeamSummary() throws Exception {
        return jiraService.getArcXpTeamSummary();
    }

    @GetMapping("/datahubTeamSummary")
    public TeamSprintSummary getDataHubTeamSummary() throws Exception {
        return jiraService.getDataHubTeamSummary();
    }

    @GetMapping("/arcxpSprint")
    public List<UserSprintData> getArcXPSprint() throws Exception {
        return jiraService.getArcXPSprintData();
    }

    @GetMapping("/arcxpTeamSummaryBySprint")
    public TeamSprintSummary getArcXpTeamSummaryBySprint(
            @RequestParam(required = false) String sprintName) throws Exception {
        return jiraService.getArcXpTeamSummaryBySprint(sprintName);
    }

    @GetMapping("/datahubTeamSummaryBySprint")
    public TeamSprintSummary getDataHubTeamSummaryBySprint(
            @RequestParam(required = false) String sprintName) throws Exception {
        return jiraService.getDataHubTeamSummaryBySprint(sprintName);
    }

    @GetMapping("/datahubDefectLeakage")
    public Map<String, Object> getDataHubDefectLeakage(
            @RequestParam String sprint) throws Exception {
        return jiraService.getDataHubDefectLeakageBySprint(sprint);
    }

    @GetMapping("/arcxpDefectLeakage")
    public Map<String, Object> getArcXpDefectLeakage(
            @RequestParam String sprint) throws Exception {
        return jiraService.getArcXpDefectLeakageBySprint(sprint);
    }

    @GetMapping("/datahubExecutionRate")
    public Map<String, Object> getDataHubExecutionRate(
            @RequestParam String sprint) throws Exception {
        return jiraService.getDataHubExecutionRateBySprint(sprint);
    }

    @GetMapping("/arcxpExecutionRate")
    public Map<String, Object> getArcXpExecutionRate(
            @RequestParam String sprint) throws Exception {
        return jiraService.getArcXpExecutionRateBySprint(sprint);
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/automationCoverage")
    public Map<String, Object> getAutomationCoverage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("automation-coverage.json");
        return mapper.readValue(resource.getInputStream(), Map.class);
    }

    @GetMapping("/downloadReport")
    public ResponseEntity<byte[]> downloadReport(
            @RequestParam String sprint,
            @RequestParam String team,
            @RequestParam(required = false) String user,
            @RequestParam String type) throws Exception {
        byte[] pdf;
        if ("team".equalsIgnoreCase(type)) {
            pdf = jiraService.generateTeamSprintPdf(
                    sprint,
                    team);
        } else {
            pdf = jiraService.generateUserSprintPdf(
                    sprint,
                    team,
                    user);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData(
                "attachment",
                "Sprint_Report.pdf");
        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}

