package com.qa.dashboard.jira_dashboard;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class JiraService {

  private static final int DATAHUB_PLANNED_SUBTASKS_PER_SPRINT = 15;
  private static final int ARCXP_PLANNED_SUBTASKS_PER_SPRINT = 15;

  private enum WorkflowTeam {
    ARC_XP,
    DATA_HUB
  }

  private static final Set<String> ARCXP_TODO_STATUSES = Set.of(
      "to do",
      "in progress",
      "code review");
  private static final Set<String> ARCXP_IN_QA_STATUSES = Set.of("in qa");
  private static final Set<String> ARCXP_REVIEW_STATUSES = Set.of("po review");
  private static final Set<String> ARCXP_BLOCKED_STATUSES = Set.of("blocked");
  private static final Set<String> ARCXP_DONE_STATUSES = Set.of("done");

  private static final Set<String> DATAHUB_TODO_STATUSES = Set.of(
      "open",
      "reopened",
      "code review",
      "in progress");
  private static final Set<String> DATAHUB_IN_QA_STATUSES = Set.of(
      "task needs rework",
      "ready for qa",
      "qa in progress");
  private static final Set<String> DATAHUB_REVIEW_STATUSES = Set.of(
      "owner validation",
      "ready for po",
      "ready for prod (in the release)");
  private static final Set<String> DATAHUB_BLOCKED_STATUSES = Set.of(
      "blocked",
      "on hold");
  private static final Set<String> DATAHUB_DONE_STATUSES = Set.of(
      "done",
      "closed",
      "resolved");

  private final JiraProperties jiraProperties;
  private volatile CacheEntry<Map<String, Map<String, String>>> sprintInfoCache;
  private final Map<String, CacheEntry<String>> ticketSearchCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry<List<UserSprintData>>> userSprintCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry<Integer>> issueCountCache = new ConcurrentHashMap<>();
  private final Map<String, CacheEntry<Map<String, Object>>> qaTurnaroundCache = new ConcurrentHashMap<>();

  public JiraService(JiraProperties jiraProperties) {
    this.jiraProperties = jiraProperties;
  }

  public void clearCaches() {
    sprintInfoCache = null;
    ticketSearchCache.clear();
    userSprintCache.clear();
    issueCountCache.clear();
    qaTurnaroundCache.clear();
  }

  // ============================
  // Jira Connectivity / Raw Data
  // ============================

  public String getJiraTickets() {
    String jql = JqlConstants.DASHBOARD_TICKETS;
    String cachedBody = getFromCache(ticketSearchCache.get(jql), getTicketsCacheTtlMs());
    if (cachedBody != null) {
      return cachedBody;
    }
    disableSSL();
    java.net.URI uri = UriComponentsBuilder
        .fromUriString(jiraProperties.getUrl() + "rest/api/2/search")
        .queryParam("jql", jql)
        .queryParam("maxResults", 100)
        .build()
        .encode()
        .toUri();
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    setJiraAuth(headers);
    headers.set("Accept", "application/json");
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        entity,
        String.class);
    String body = response.getBody();
    if (body != null) {
      ticketSearchCache.put(jql, new CacheEntry<>(body, System.currentTimeMillis()));
    }
    return body;
  }

  // =====================
  // Sprint Metadata (Tile)
  // =====================

  private int getLatestBoardId(String projectKey, RestTemplate restTemplate, HttpEntity<String> entity,
      ObjectMapper mapper) {
    try {
      String boardUrl = jiraProperties.getUrl() + "rest/agile/1.0/board?projectKeyOrId=" + projectKey;
      ResponseEntity<String> boardResponse = restTemplate.exchange(boardUrl, HttpMethod.GET, entity, String.class);
      JsonNode boardRoot = mapper.readTree(boardResponse.getBody());
      int selectedBoardId = -1;
      String latestActivatedDate = "";
      for (JsonNode board : boardRoot.get("values")) {
        int boardId = board.get("id").asInt();
        try {
          String sprintUrl = jiraProperties.getUrl() + "rest/agile/1.0/board/" + boardId + "/sprint?state=active";
          ResponseEntity<String> sprintResponse = restTemplate.exchange(sprintUrl, HttpMethod.GET, entity,
              String.class);
          JsonNode sprintRoot = mapper.readTree(sprintResponse.getBody());
          if (sprintRoot.get("values").size() > 0) {
            JsonNode sprint = sprintRoot.get("values").get(0);
            if (sprint.has("activatedDate")) {
              String currentDate = sprint.get("activatedDate").asText();
              if (currentDate.compareTo(latestActivatedDate) > 0) {
                latestActivatedDate = currentDate;
                selectedBoardId = boardId;
              }
            }
          }
        } catch (Exception e) {
          continue;
        }
      }
      if (selectedBoardId == -1 && boardRoot.get("values").size() > 0) {
        selectedBoardId = boardRoot.get("values").get(0).get("id").asInt();
      }
      return selectedBoardId;
    } catch (Exception e) {
      e.printStackTrace();
      return -1;
    }
  }

  private int getProjectIssueCountForSprint(String projectKey, JsonNode sprint) {
    if (projectKey == null || projectKey.isBlank() || sprint == null || sprint.isNull()) {
      return 0;
    }
    try {
      if (sprint.has("id") && !sprint.get("id").isNull()) {
        int sprintId = sprint.get("id").asInt(-1);
        if (sprintId > 0) {
          return fetchIssueCountByJql("project = " + projectKey + " AND sprint = " + sprintId);
        }
      }
      if (sprint.has("name") && !sprint.get("name").isNull()) {
        String sprintName = sprint.get("name").asText("").trim();
        if (!sprintName.isEmpty()) {
          String escapedName = sprintName.replace("\"", "\\\"");
          return fetchIssueCountByJql("project = " + projectKey + " AND sprint = \"" + escapedName + "\"");
        }
      }
    } catch (Exception e) {
      return 0;
    }
    return 0;
  }

  public Map<String, Map<String, String>> getSprintInfo() {
    Map<String, Map<String, String>> cached = getFromCache(sprintInfoCache, getSprintInfoCacheTtlMs());
    if (cached != null) {
      return cached;
    }
    disableSSL();
    Map<String, Map<String, String>> result = new HashMap<>();
    try {
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      setJiraAuth(headers);
      headers.set("Accept", "application/json");
      HttpEntity<String> entity = new HttpEntity<>(headers);
      ObjectMapper mapper = new ObjectMapper();
      for (String projectKey : jiraProperties.getSprint().getProjects()) {
        Map<String, String> sprintInfo = new HashMap<>();

        int boardId = getLatestBoardId(projectKey, restTemplate, entity, mapper);
        if (boardId == -1) {
          sprintInfo.put("name", "No Board");
          sprintInfo.put("status", "INACTIVE");
          sprintInfo.put("history", "");
          result.put(projectKey, sprintInfo);
          continue;
        }

        List<JsonNode> allSprints = new ArrayList<>();
        int startAt = 0;
        boolean isLast = false;
        while (!isLast) {
          String sprintUrl = jiraProperties.getUrl()
              + "rest/agile/1.0/board/" + boardId
              + "/sprint?state=active,closed&maxResults=50&startAt=" + startAt;
          ResponseEntity<String> sprintResponse = restTemplate.exchange(sprintUrl, HttpMethod.GET, entity,
              String.class);
          JsonNode sprintRoot = mapper.readTree(sprintResponse.getBody());
          for (JsonNode sprint : sprintRoot.get("values")) {
            allSprints.add(sprint);
          }
          isLast = sprintRoot.get("isLast").asBoolean();
          startAt += 50;
        }
        allSprints.sort((a, b) -> {
          String d1 = a.has("startDate") ? a.get("startDate").asText() : "";
          String d2 = b.has("startDate") ? b.get("startDate").asText() : "";
          return d2.compareTo(d1);
        });

        String currentSprint = "No Sprint";
        int bestActiveCount = -1;
        List<String> history = new ArrayList<>();

        for (JsonNode sprint : allSprints) {
          String state = sprint.has("state") ? sprint.get("state").asText("") : "";
          if (!state.equalsIgnoreCase("active")) {
            continue;
          }
          int count = getProjectIssueCountForSprint(projectKey, sprint);
          if (count > bestActiveCount) {
            bestActiveCount = count;
            currentSprint = sprint.has("name") ? sprint.get("name").asText("No Sprint") : "No Sprint";
          }
        }
        for (JsonNode sprint : allSprints) {
          String name = sprint.get("name").asText();
          String state = sprint.get("state").asText();
          if (!state.equalsIgnoreCase("closed")) {
            continue;
          }
          if (name.equals(currentSprint)) {
            continue;
          }
          int count = getProjectIssueCountForSprint(projectKey, sprint);
          if (count > 0) {
            history.add(name);
          }
          if (history.size() >= 2) {
            break;
          }
        }
        sprintInfo.put("name", currentSprint);
        sprintInfo.put("history", String.join(",", history));
        sprintInfo.put("status", currentSprint.equals("No Sprint") ? "INACTIVE" : "ACTIVE");
        result.put(projectKey, sprintInfo);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    sprintInfoCache = new CacheEntry<>(result, System.currentTimeMillis());
    return result;
  }

  // ==============================
  // User Sprint Data (Team: Arc Xp)
  // ==============================

  public List<UserSprintData> getArcXPSprintData() throws Exception {
    return fetchUserSprintData(JqlConstants.ARCXP_OPEN_SPRINT, WorkflowTeam.ARC_XP);
  }

  public List<UserSprintData> getArcXPSprintDataBySprint(String sprintName) throws Exception {
    if (sprintName == null || sprintName.trim().isEmpty()) {
      throw new IllegalArgumentException("Sprint name cannot be empty");
    }
    sprintName = sprintName.trim();
    String jql = JqlConstants.buildArcXpSprintWorkflowJql(sprintName);
    return fetchUserSprintData(jql, WorkflowTeam.ARC_XP);
  }

  public List<UserSprintData> getDataHubSprintData() throws Exception {
    return fetchUserSprintData(JqlConstants.DATAHUB_OPEN_SPRINT, WorkflowTeam.DATA_HUB);
  }

  public List<UserSprintData> getDataHubSprintDataBySprint(String sprintName) throws Exception {
    if (sprintName == null || sprintName.trim().isEmpty()) {
      throw new IllegalArgumentException("Sprint name cannot be empty");
    }
    sprintName = sprintName.trim();
    String jql = JqlConstants.buildDataHubSprintWorkflowJql(sprintName);
    return fetchUserSprintData(jql, WorkflowTeam.DATA_HUB);
  }

  // Shared helper for fetching user sprint data by JQL.
  private List<UserSprintData> fetchUserSprintData(String jql, WorkflowTeam team) throws Exception {
    String cacheKey = team.name() + "::workflow-v3::" + jql;
    List<UserSprintData> cached = getFromCache(userSprintCache.get(cacheKey), getUserSprintCacheTtlMs());
    if (cached != null) {
      return cached;
    }
    disableSSL();
    java.net.URI uri = UriComponentsBuilder
        .fromUriString(jiraProperties.getUrl() + "rest/api/2/search")
        .queryParam("jql", jql)
        .queryParam("maxResults", 500)
        .queryParam("expand", "names")
        .build()
        .encode()
        .toUri();
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    setJiraAuth(headers);
    headers.set("Accept", "application/json");
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        entity,
        String.class);
    String body = response.getBody();
    if (body == null || body.isEmpty()) {
      throw new RuntimeException("Empty response from Jira");
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(body);
    String qaByFieldKey = resolveQaByFieldKey(root);
    Map<String, UserSprintData> map = new HashMap<>();
    for (JsonNode issue : root.get("issues")) {
      JsonNode fields = issue.get("fields");
      String status = fields.get("status").get("name").asText().toLowerCase();
      String issueType = fields.get("issuetype").get("name").asText();
      String qaByDisplayName = resolveQaByDisplayName(fields, qaByFieldKey);
      String ownerName = qaByDisplayName;
      // For tasks, if QA by is unassigned, check assignee field
      if ("Unassigned".equals(qaByDisplayName) && "Task".equalsIgnoreCase(issueType)) {
        JsonNode assigneeNode = fields.get("assignee");
        String assigneeName = resolveUserNameFromNode(assigneeNode);
        if (!"Unassigned".equals(assigneeName)) {
          ownerName = assigneeName;
        }
      }
      map.putIfAbsent(ownerName, new UserSprintData(ownerName));
      UserSprintData user = map.get(ownerName);
      user.setTotal(user.getTotal() + 1);
      incrementWorkflowBucket(user, status, team);
      if ("Bug".equalsIgnoreCase(issueType)) {
        user.setBugs(user.getBugs() + 1);
      }
    }
    List<UserSprintData> result = new ArrayList<>(map.values());
    userSprintCache.put(cacheKey, new CacheEntry<>(result, System.currentTimeMillis()));
    return result;
  }

  private String resolveQaByFieldKey(JsonNode root) {
    JsonNode names = root.get("names");
    if (names == null || names.isNull()) {
      return null;
    }
    Iterator<Map.Entry<String, JsonNode>> fieldEntries = names.properties().iterator();
    while (fieldEntries.hasNext()) {
      Map.Entry<String, JsonNode> entry = fieldEntries.next();
      String label = entry.getValue() != null ? entry.getValue().asText("") : "";
      if ("QA by".equalsIgnoreCase(label.trim())) {
        return entry.getKey();
      }
    }
    return null;
  }

  private String resolveQaByDisplayName(JsonNode issueFields, String qaByFieldKey) {
    if (qaByFieldKey == null || qaByFieldKey.isBlank() || issueFields == null || issueFields.isNull()) {
      return "Unassigned";
    }
    JsonNode qaByNode = issueFields.get(qaByFieldKey);
    if (qaByNode == null || qaByNode.isNull()) {
      return "Unassigned";
    }
    if (qaByNode.isArray()) {
      if (qaByNode.size() == 0) {
        return "Unassigned";
      }
      JsonNode first = qaByNode.get(0);
      return resolveUserNameFromNode(first);
    }
    return resolveUserNameFromNode(qaByNode);
  }

  private String resolveUserNameFromNode(JsonNode userNode) {
    if (userNode == null || userNode.isNull()) {
      return "Unassigned";
    }
    if (userNode.isTextual()) {
      String name = userNode.asText().trim();
      return name.isEmpty() ? "Unassigned" : name;
    }
    JsonNode displayName = userNode.get("displayName");
    if (displayName != null && !displayName.isNull() && !displayName.asText().trim().isEmpty()) {
      return displayName.asText().trim();
    }
    JsonNode name = userNode.get("name");
    if (name != null && !name.isNull() && !name.asText().trim().isEmpty()) {
      return name.asText().trim();
    }
    return "Unassigned";
  }

  private int fetchIssueCountByJql(String jql) throws Exception {
    Integer cachedCount = getFromCache(issueCountCache.get(jql), getIssueCountCacheTtlMs());
    if (cachedCount != null) {
      return cachedCount;
    }
    disableSSL();
    java.net.URI uri = UriComponentsBuilder
        .fromUriString(jiraProperties.getUrl() + "rest/api/2/search")
        .queryParam("jql", jql)
        .queryParam("maxResults", 0)
        .build()
        .encode()
        .toUri();

    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();
    setJiraAuth(headers);
    headers.set("Accept", "application/json");
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        uri,
        HttpMethod.GET,
        entity,
        String.class);

    String body = response.getBody();
    if (body == null || body.isEmpty()) {
      throw new RuntimeException("Empty response from Jira");
    }
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(body);
    JsonNode total = root.get("total");
    int result = total != null ? total.asInt() : 0;
    issueCountCache.put(jql, new CacheEntry<>(result, System.currentTimeMillis()));
    return result;
  }

  private double roundToTwoDecimals(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private long getSprintInfoCacheTtlMs() {
    return getTtlWithFallback(jiraProperties.getCache().getSprintInfoMs(), 60000);
  }

  private long getTicketsCacheTtlMs() {
    return getTtlWithFallback(jiraProperties.getCache().getTicketsMs(), 60000);
  }

  private long getUserSprintCacheTtlMs() {
    return getTtlWithFallback(jiraProperties.getCache().getUserSprintMs(), 60000);
  }

  private long getIssueCountCacheTtlMs() {
    return getTtlWithFallback(jiraProperties.getCache().getIssueCountMs(), 180000);
  }

  private long getTtlWithFallback(long configuredMs, long fallbackMs) {
    return configuredMs > 0 ? configuredMs : fallbackMs;
  }

  private void setJiraAuth(HttpHeaders headers) {
    String token = jiraProperties.getToken();
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("JIRA_PAT is not configured");
    }
    headers.setBearerAuth(token);
  }

  private <T> T getFromCache(CacheEntry<T> entry, long ttlMs) {
    if (entry == null) {
      return null;
    }
    long now = System.currentTimeMillis();
    if ((now - entry.cachedAtMs) < ttlMs) {
      return entry.value;
    }
    return null;
  }

  private static class CacheEntry<T> {
    private final T value;
    private final long cachedAtMs;

    private CacheEntry(T value, long cachedAtMs) {
      this.value = value;
      this.cachedAtMs = cachedAtMs;
    }
  }

  // ======================
  // Team Summary Aggregates
  // ======================

  public TeamSprintSummary getArcXpTeamSummary() throws Exception {
    List<UserSprintData> users = getArcXPSprintData();
    return buildTeamSummary("Arc Xp", users);
  }

  public TeamSprintSummary getArcXpTeamSummaryBySprint(String sprintName) throws Exception {
    List<UserSprintData> users = getArcXPSprintDataBySprint(sprintName);
    return buildTeamSummary("Arc Xp", users);
  }

  public TeamSprintSummary getDataHubTeamSummary() throws Exception {
    List<UserSprintData> users = getDataHubSprintData();
    return buildTeamSummary("Data Hub", users);
  }

  public TeamSprintSummary getDataHubTeamSummaryBySprint(String sprintName) throws Exception {
    List<UserSprintData> users = getDataHubSprintDataBySprint(sprintName);
    return buildTeamSummary("Data Hub", users);
  }

  private TeamSprintSummary buildTeamSummary(String teamName, List<UserSprintData> users) {
    TeamSprintSummary summary = new TeamSprintSummary();
    summary.setTeam(teamName);
    summary.setUsers(users);
    int total = 0;
    int todo = 0;
    int inQa = 0;
    int review = 0;
    int blocked = 0;
    int done = 0;
    int bugs = 0;
    for (UserSprintData user : users) {
      total += user.getTotal();
      todo += user.getTodo();
      inQa += user.getInQa();
      review += user.getReview();
      blocked += user.getBlocked();
      done += user.getDone();
      bugs += user.getBugs();
    }
    summary.setTotal(total);
    summary.setTodo(todo);
    summary.setInQa(inQa);
    summary.setReview(review);
    summary.setBlocked(blocked);
    summary.setDone(done);
    summary.setBugs(bugs);
    return summary;
  }

  private void incrementWorkflowBucket(UserSprintData user, String status, WorkflowTeam team) {
    if (status == null) {
      user.setTodo(user.getTodo() + 1);
      return;
    }
    Set<String> todoStatuses = team == WorkflowTeam.DATA_HUB ? DATAHUB_TODO_STATUSES : ARCXP_TODO_STATUSES;
    Set<String> inQaStatuses = team == WorkflowTeam.DATA_HUB ? DATAHUB_IN_QA_STATUSES : ARCXP_IN_QA_STATUSES;
    Set<String> reviewStatuses = team == WorkflowTeam.DATA_HUB ? DATAHUB_REVIEW_STATUSES : ARCXP_REVIEW_STATUSES;
    Set<String> blockedStatuses = team == WorkflowTeam.DATA_HUB ? DATAHUB_BLOCKED_STATUSES : ARCXP_BLOCKED_STATUSES;
    Set<String> doneStatuses = team == WorkflowTeam.DATA_HUB ? DATAHUB_DONE_STATUSES : ARCXP_DONE_STATUSES;
    if (todoStatuses.contains(status)) {
      user.setTodo(user.getTodo() + 1);
    } else if (inQaStatuses.contains(status)) {
      user.setInQa(user.getInQa() + 1);
    } else if (reviewStatuses.contains(status)) {
      user.setReview(user.getReview() + 1);
    } else if (blockedStatuses.contains(status)) {
      user.setBlocked(user.getBlocked() + 1);
    } else if (doneStatuses.contains(status)) {
      user.setDone(user.getDone() + 1);
    } else {
      if (team == WorkflowTeam.ARC_XP) {
        user.setTodo(user.getTodo() + 1);
      }
    }
  }

  // =================
  // KPI Calculations
  // =================

  public int getStoryCount() throws Exception {
    return fetchIssueCountByJql(JqlConstants.STORIES_FOR_DEFECT_DENSITY);
  }

  public int getBugCount() throws Exception {
    return fetchIssueCountByJql(JqlConstants.BUGS_FOR_DEFECT_DENSITY);
  }

  public Map<String, Object> getDataHubDefectLeakageBySprint(String sprintNameOrId) throws Exception {
    String bugsJql = JqlConstants.buildDataHubDefectLeakageBugsJql(sprintNameOrId);
    String bugSubTasksJql = JqlConstants.buildDataHubDefectLeakageBugSubTasksJql(sprintNameOrId);
    String productionBugsJql = JqlConstants.buildDataHubProductionDefectsJql(sprintNameOrId);
    int bugCount = fetchIssueCountByJql(bugsJql);
    int bugSubTaskCount = fetchIssueCountByJql(bugSubTasksJql);
    int totalDefects = bugCount + bugSubTaskCount;
    int productionDefects = fetchIssueCountByJql(productionBugsJql);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("team", "Data Hub");
    result.put("sprint", sprintNameOrId);
    result.put("bugsJql", bugsJql);
    result.put("bugSubTasksJql", bugSubTasksJql);
    result.put("productionBugsJql", productionBugsJql);
    result.put("bugCount", bugCount);
    result.put("bugSubTaskCount", bugSubTaskCount);
    result.put("totalDefects", totalDefects);
    result.put("productionDefects", productionDefects);
    result.put("defectLeakageRatePercent", totalDefects == 0
        ? null
        : roundToTwoDecimals(((double) productionDefects / totalDefects) * 100.0));
    return result;
  }

  public Map<String, Object> getArcXpDefectLeakageBySprint(String sprintNameOrId) throws Exception {
    String bugsJql = JqlConstants.buildArcXpDefectLeakageBugsJql(sprintNameOrId);
    String productionBugsJql = JqlConstants.buildArcXpProductionDefectsJql(sprintNameOrId);
    int totalDefects = fetchIssueCountByJql(bugsJql);
    int productionDefects = fetchIssueCountByJql(productionBugsJql);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("team", "Arc Xp");
    result.put("sprint", sprintNameOrId);
    result.put("bugsJql", bugsJql);
    result.put("productionBugsJql", productionBugsJql);
    result.put("totalDefects", totalDefects);
    result.put("productionDefects", productionDefects);
    result.put("defectLeakageRatePercent", totalDefects == 0
        ? null
        : roundToTwoDecimals(((double) productionDefects / totalDefects) * 100.0));
    return result;
  }

  public Map<String, Object> getDataHubExecutionRateBySprint(String sprintNameOrId) throws Exception {
    String executedSubTasksJql = JqlConstants.buildDataHubExecutionRateSubTasksJql(sprintNameOrId);
    int executedSubTasks = fetchIssueCountByJql(executedSubTasksJql);
    int plannedSubTasks = DATAHUB_PLANNED_SUBTASKS_PER_SPRINT;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("team", "Data Hub");
    result.put("sprint", sprintNameOrId);
    result.put("executedSubTasksJql", executedSubTasksJql);
    result.put("executedSubTasks", executedSubTasks);
    result.put("plannedSubTasks", plannedSubTasks);
    result.put("executionRatePercent", plannedSubTasks == 0
        ? null
        : roundToTwoDecimals(((double) executedSubTasks / plannedSubTasks) * 100.0));
    return result;
  }

  public Map<String, Object> getArcXpExecutionRateBySprint(String sprintNameOrId) throws Exception {
    String executedSubTasksJql = JqlConstants.buildArcXpExecutionRateSubTasksJql(sprintNameOrId);
    int executedSubTasks = fetchIssueCountByJql(executedSubTasksJql);
    int plannedSubTasks = ARCXP_PLANNED_SUBTASKS_PER_SPRINT;
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("team", "Arc Xp");
    result.put("sprint", sprintNameOrId);
    result.put("executedSubTasksJql", executedSubTasksJql);
    result.put("executedSubTasks", executedSubTasks);
    result.put("plannedSubTasks", plannedSubTasks);
    result.put("executionRatePercent", plannedSubTasks == 0
        ? null
        : roundToTwoDecimals(((double) executedSubTasks / plannedSubTasks) * 100.0));
    return result;
  }

  // ====================
  // PDF Report Generation
  // ====================

  private boolean isDataHubTeam(String team) {
    return "Data Hub".equalsIgnoreCase(team);
  }

  private TeamSprintSummary getTeamSummaryBySprint(String sprint, String team) throws Exception {
    return isDataHubTeam(team)
        ? getDataHubTeamSummaryBySprint(sprint)
        : getArcXpTeamSummaryBySprint(sprint);
  }

  private int getStoryCountByTeamAndSprint(String sprint, String team) throws Exception {
    String jql = isDataHubTeam(team)
        ? JqlConstants.buildDataHubStoriesJqlBySprint(sprint)
        : JqlConstants.buildStoriesJqlBySprint(sprint);
    return fetchIssueCountByJql(jql);
  }

  private int getBugCountByTeamAndSprint(String sprint, String team) throws Exception {
    String jql = isDataHubTeam(team)
        ? JqlConstants.buildDataHubBugsJqlBySprint(sprint)
        : JqlConstants.buildBugsJqlBySprint(sprint);
    return fetchIssueCountByJql(jql);
  }

  public byte[] generateTeamSprintPdf(
      String sprint,
      String team) {
    try {
      TeamSprintSummary summary = getTeamSummaryBySprint(sprint, team);
      int storyCount = getStoryCountByTeamAndSprint(sprint, team);
      int bugCount = getBugCountByTeamAndSprint(sprint, team);
      String defectLeakageValue = "--";
      String executionRateValue = "--";
      if (isDataHubTeam(team)) {
        Map<String, Object> leakageKpi = getDataHubDefectLeakageBySprint(sprint);
        Object leakagePercent = leakageKpi.get("defectLeakageRatePercent");
        if (leakagePercent instanceof Number) {
          defectLeakageValue = roundToTwoDecimals(((Number) leakagePercent).doubleValue()) + "%";
        }
        Map<String, Object> executionRateKpi = getDataHubExecutionRateBySprint(sprint);
        Object executionRatePercent = executionRateKpi.get("executionRatePercent");
        if (executionRatePercent instanceof Number) {
          executionRateValue = roundToTwoDecimals(((Number) executionRatePercent).doubleValue()) + "%";
        }
      } else {
        Map<String, Object> leakageKpi = getArcXpDefectLeakageBySprint(sprint);
        Object leakagePercent = leakageKpi.get("defectLeakageRatePercent");
        if (leakagePercent instanceof Number) {
          defectLeakageValue = roundToTwoDecimals(((Number) leakagePercent).doubleValue()) + "%";
        }
        Map<String, Object> executionRateKpi = getArcXpExecutionRateBySprint(sprint);
        Object executionRatePercent = executionRateKpi.get("executionRatePercent");
        if (executionRatePercent instanceof Number) {
          executionRateValue = roundToTwoDecimals(((Number) executionRatePercent).doubleValue()) + "%";
        }
      }
      summary.setStories(storyCount);
      summary.setBugs(bugCount);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 40, 40, 50, 50);
      PdfWriter.getInstance(document, out);
      document.open();
      Font titleFont = new Font(
          Font.FontFamily.HELVETICA,
          24,
          Font.BOLD,
          BaseColor.BLACK);
      Font headingFont = new Font(
          Font.FontFamily.HELVETICA,
          15,
          Font.BOLD,
          new BaseColor(33, 64, 154));
      Font normalFont = new Font(
          Font.FontFamily.HELVETICA,
          11,
          Font.NORMAL,
          BaseColor.DARK_GRAY);
      Font kpiValueFont = new Font(
          Font.FontFamily.HELVETICA,
          13,
          Font.BOLD,
          new BaseColor(33, 64, 154));
      Font tableHeaderFont = new Font(
          Font.FontFamily.HELVETICA,
          11,
          Font.BOLD,
          BaseColor.WHITE);
      Font glossaryFont = new Font(
          Font.FontFamily.HELVETICA,
          8,
          Font.NORMAL,
          BaseColor.GRAY);
      Paragraph title = new Paragraph(
          "TEAM SPRINT REPORT",
          titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      document.add(title);
      document.add(new Paragraph(" "));
      Paragraph details = new Paragraph();
      details.add(new Chunk("Team : ", headingFont));
      details.add(new Chunk(team + "\n", normalFont));
      details.add(new Chunk("Sprint : ", headingFont));
      details.add(new Chunk(sprint, normalFont));
      document.add(details);
      document.add(new Paragraph(" "));
      document.add(new Paragraph(" "));
      Paragraph kpiHeading = new Paragraph(
          "Team KPIs",
          headingFont);
      document.add(kpiHeading);
      document.add(new Paragraph(" "));
      PdfPTable kpiTable = new PdfPTable(2);
      kpiTable.setWidthPercentage(100);
      kpiTable.setSpacingBefore(10f);
      kpiTable.setWidths(new float[] {
          3.5f, 1.5f
      });
      String[][] kpis = {
          { "Defect Leakage Rate %", defectLeakageValue },
          { "Test Effectiveness %", "--" },
          { "Execution Rate %", executionRateValue },
          { "Automation Coverage %", "--" },
          { "Automation Pass Rate %", "--" }
      };

      PdfPCell kpiHeaderCell = new PdfPCell(new Phrase("KPI", tableHeaderFont));
      kpiHeaderCell.setBackgroundColor(new BaseColor(37, 71, 221));
      kpiHeaderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
      kpiHeaderCell.setPadding(10);
      kpiTable.addCell(kpiHeaderCell);

      PdfPCell valueHeaderCell = new PdfPCell(new Phrase("Value", tableHeaderFont));
      valueHeaderCell.setBackgroundColor(new BaseColor(37, 71, 221));
      valueHeaderCell.setHorizontalAlignment(Element.ALIGN_CENTER);
      valueHeaderCell.setPadding(10);
      kpiTable.addCell(valueHeaderCell);

      for (String[] kpi : kpis) {
        PdfPCell nameCell = new PdfPCell(new Phrase(kpi[0], normalFont));
        nameCell.setPadding(10);
        nameCell.setBackgroundColor(new BaseColor(245, 247, 252));
        nameCell.setBorderColor(new BaseColor(220, 220, 220));
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        kpiTable.addCell(nameCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(kpi[1], kpiValueFont));
        valueCell.setPadding(10);
        valueCell.setBackgroundColor(new BaseColor(245, 247, 252));
        valueCell.setBorderColor(new BaseColor(220, 220, 220));
        valueCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        kpiTable.addCell(valueCell);
      }
      document.add(kpiTable);
      document.add(new Paragraph("\n\n\n\n\n\n\n\n\n\n\n"));
      Paragraph g1 = new Paragraph(
        "Defect Leakage Rate % = Defects found in production / Total Defects Raised",
          glossaryFont);
      g1.setAlignment(Element.ALIGN_RIGHT);
      document.add(g1);
      Paragraph g2 = new Paragraph(
        "Test Effectiveness % = Defects found during testing / Total Defects * 100",
          glossaryFont);
      g2.setAlignment(Element.ALIGN_RIGHT);
      document.add(g2);
      Paragraph g3 = new Paragraph(
          "Execution Rate % = Executed QA subtasks / planned QA subtasks * 100",
          glossaryFont);
      g3.setAlignment(Element.ALIGN_RIGHT);
      document.add(g3);
        Paragraph g4 = new Paragraph(
          "Automation Coverage % = Automated test cases / Total Regression test cases * 100",
          glossaryFont);
        g4.setAlignment(Element.ALIGN_RIGHT);
        document.add(g4);
        Paragraph g5 = new Paragraph(
          "Automation Pass Rate % = Passed automation tests / Executed Automated tests * 100",
          glossaryFont);
        g5.setAlignment(Element.ALIGN_RIGHT);
        document.add(g5);
      document.close();
      return out.toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
      return new byte[0];
    }
  }

  public byte[] generateUserSprintPdf(
      String sprint,
      String team,
      String userName) {
    try {
      TeamSprintSummary summary = getTeamSummaryBySprint(sprint, team);
      UserSprintData selectedUser = null;
      for (UserSprintData user : summary.getUsers()) {
        if (user.getName().equalsIgnoreCase(userName)) {
          selectedUser = user;
          break;
        }
      }
      if (selectedUser == null) {
        throw new RuntimeException("User not found");
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      Document document = new Document(PageSize.A4, 40, 40, 50, 50);
      PdfWriter.getInstance(document, out);
      document.open();
      Font titleFont = new Font(
          Font.FontFamily.HELVETICA,
          24,
          Font.BOLD,
          BaseColor.BLACK);
      Font headingFont = new Font(
          Font.FontFamily.HELVETICA,
          15,
          Font.BOLD,
          new BaseColor(33, 64, 154));
      Font normalFont = new Font(
          Font.FontFamily.HELVETICA,
          11,
          Font.NORMAL,
          BaseColor.DARK_GRAY);
      Font tableHeaderFont = new Font(
          Font.FontFamily.HELVETICA,
          11,
          Font.BOLD,
          BaseColor.WHITE);
      Font tableValueFont = new Font(
          Font.FontFamily.HELVETICA,
          11,
          Font.NORMAL,
          BaseColor.BLACK);
      Paragraph title = new Paragraph(
          "USER SPRINT REPORT",
          titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      document.add(title);
      document.add(new Paragraph(" "));
      Paragraph details = new Paragraph();
      details.add(new Chunk("User : ", headingFont));
      details.add(new Chunk(userName + "\n", normalFont));
      details.add(new Chunk("Team : ", headingFont));
      details.add(new Chunk(team + "\n", normalFont));
      details.add(new Chunk("Sprint : ", headingFont));
      details.add(new Chunk(sprint, normalFont));
      document.add(details);
      document.add(new Paragraph(" "));
      document.add(new Paragraph(" "));
      Paragraph workflowHeading = new Paragraph(
          "Workflow Metrics",
          headingFont);
      document.add(workflowHeading);
      document.add(new Paragraph(" "));
      PdfPTable table = new PdfPTable(6);
      table.setWidthPercentage(100);
      table.setSpacingBefore(10f);
      table.setWidths(new float[] {
          2f, 2f, 2f, 2f, 2f, 2f
      });
      String[] headers = {
          "Total",
          "To Do",
          "In QA",
          "Review",
          "Blocked",
          "Done"
      };
      for (String h : headers) {
        PdfPCell cell = new PdfPCell(
            new Phrase(h, tableHeaderFont));
        cell.setBackgroundColor(
            new BaseColor(37, 71, 221));
        cell.setHorizontalAlignment(
            Element.ALIGN_CENTER);
        cell.setPadding(10);
        table.addCell(cell);
      }
      String[] values = {
          String.valueOf(selectedUser.getTotal()),
          String.valueOf(selectedUser.getTodo()),
          String.valueOf(selectedUser.getInQa()),
          String.valueOf(selectedUser.getReview()),
          String.valueOf(selectedUser.getBlocked()),
          String.valueOf(selectedUser.getDone())
      };
      for (String v : values) {
        PdfPCell cell = new PdfPCell(
            new Phrase(v, tableValueFont));
        cell.setHorizontalAlignment(
            Element.ALIGN_CENTER);
        cell.setPadding(12);
        table.addCell(cell);
      }
      document.add(table);
      document.add(new Paragraph(" "));
      document.add(new Paragraph(" "));
      document.close();
      return out.toByteArray();
    } catch (Exception e) {
      e.printStackTrace();
      return new byte[0];
    }
  }

  // =================
  // Security Utilities
  // =================

  private void disableSSL() {
    try {
      javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
          new javax.net.ssl.X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
              return null;
            }

            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }
          }
      };
      javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
