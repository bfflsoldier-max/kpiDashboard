package com.qa.dashboard.jira_dashboard;

public final class JqlConstants {

    private static final String ARCXP_PROJECT_KEY = "BMARC";
    private static final String ARCXP_USERS = "(ragi10253, prgi10098, magi10260)";
    private static final String DATAHUB_PROJECT_KEY = "RDSDEV";
    private static final String DATAHUB_QA_USERS = "(ksgi10243, asgi10191)";
    private static final String DATAHUB_WORKFLOW_ISSUE_TYPES = "(Bug, Improvement, Story, Task, \"Bug Sub-Task\")";

  private JqlConstants() {
    // Utility class
  }

  public static final String DASHBOARD_TICKETS = "project in (RDSDEV, BMCD, BMARC, BMP, BMF, MITAPP) "
      + "AND issuetype in (Bug, Task) "
      + "AND assignee in (prgi10098, ksgi10243, magi10260, bsgi10096, asgi10191, "
      + "nigi10316, degi10163, aigi10164, ragi10253, nigi10196, logi10238, srgi10218, dagi10147 ) "
      + "ORDER BY lastViewed DESC";

  public static final String ARCXP_OPEN_SPRINT = "project = BMARC "
      + "AND sprint in openSprints() "
      + "AND issuetype in (Bug, Story, Task, \"Bug Sub-Task\", Sub-task) "
      + "AND (\"QA by\" in " + ARCXP_USERS + " OR (issuetype = Task AND \"QA by\" is EMPTY AND assignee in " + ARCXP_USERS + "))";

  public static final String DATAHUB_OPEN_SPRINT = "project = " + DATAHUB_PROJECT_KEY + " "
      + "AND sprint in openSprints() "
      + "AND issuetype in " + DATAHUB_WORKFLOW_ISSUE_TYPES + " "
      + "AND \"QA by\" in " + DATAHUB_QA_USERS;

  // ========================================
  // KPI Calculation - Defect Density
  // ========================================

  // Stories for defect density: Grouped by "QA by" field (custom field for QA assignment)
  public static final String STORIES_FOR_DEFECT_DENSITY = "project = BMARC "
      + "AND sprint in openSprints() "
      + "AND issuetype = Story "
      + "AND \"QA by\" in (ragi10253, prgi10098, magi10260)";

  // Bugs for defect density: Counted by both assignee and reporter
  public static final String BUGS_FOR_DEFECT_DENSITY = "project = BMARC "
      + "AND sprint in openSprints() "
      + "AND issuetype = Bug "
      + "AND (assignee in (ragi10253, prgi10098, magi10260) "
      + "OR reporter in (ragi10253, prgi10098, magi10260))";

    public static String buildStoriesJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Story "
                + "AND \"QA by\" in " + ARCXP_USERS;
    }

    public static String buildBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + ARCXP_USERS + " OR reporter in " + ARCXP_USERS + ")";
    }

    public static String buildOpenBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + ARCXP_USERS + " OR reporter in " + ARCXP_USERS + ") "
                + "AND statusCategory != Done";
    }

    public static String buildClosedBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + ARCXP_USERS + " OR reporter in " + ARCXP_USERS + ") "
                + "AND statusCategory = Done";
    }

    public static String buildArcXpSprintWorkflowJql(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype in (Bug, Story, Task, \"Bug Sub-Task\", Sub-task) "
                + "AND (\"QA by\" in " + ARCXP_USERS + " OR (issuetype = Task AND \"QA by\" is EMPTY AND assignee in " + ARCXP_USERS + "))";
    }

    public static String buildDataHubStoriesJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Story "
                + "AND \"QA by\" in " + DATAHUB_QA_USERS;
    }

    public static String buildDataHubBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + DATAHUB_QA_USERS + " OR reporter in " + DATAHUB_QA_USERS + ")";
    }

    public static String buildDataHubOpenBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + DATAHUB_QA_USERS + " OR reporter in " + DATAHUB_QA_USERS + ") "
                + "AND statusCategory != Done";
    }

    public static String buildDataHubClosedBugsJqlBySprint(String sprintName) {
        String escapedSprintName = sprintName.replace("\"", "\\\"");
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND sprint = \"" + escapedSprintName + "\" "
                + "AND issuetype = Bug "
                + "AND (\"QA by\" in " + DATAHUB_QA_USERS + " OR reporter in " + DATAHUB_QA_USERS + ") "
                + "AND statusCategory = Done";
    }

    public static String buildDataHubSprintWorkflowJql(String sprintName) {
        String sprintClause = buildDataHubSprintClause(sprintName);
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + sprintClause
                + "AND issuetype in " + DATAHUB_WORKFLOW_ISSUE_TYPES + " "
                + "AND \"QA by\" in " + DATAHUB_QA_USERS;
    }

    public static String buildDataHubDefectLeakageBugsJql(String sprintNameOrId) {
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND issuetype = Bug "
                + buildDataHubSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + DATAHUB_QA_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildArcXpDefectLeakageBugsJql(String sprintNameOrId) {
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND issuetype = Bug "
                + buildArcXpSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + ARCXP_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildDataHubQaTurnaroundBugsJql(String sprintNameOrId) {
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND issuetype = Bug "
                + buildDataHubSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + DATAHUB_QA_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildDataHubDefectLeakageBugSubTasksJql(String sprintNameOrId) {
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND issuetype = \"Bug Sub-Task\" "
                + buildDataHubSprintClause(sprintNameOrId)
                + "AND reporter in " + DATAHUB_QA_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildDataHubProductionDefectsJql(String sprintNameOrId) {
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND issuetype = Bug "
                + "AND \"Epic Link\" = RDSDEV-16525 "
                + buildDataHubSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + DATAHUB_QA_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildArcXpProductionDefectsJql(String sprintNameOrId) {
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND issuetype = Bug "
                + "AND \"Epic Link\" = BMARC-8500 "
                + buildArcXpSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + ARCXP_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildDataHubExecutionRateSubTasksJql(String sprintNameOrId) {
        return "project = " + DATAHUB_PROJECT_KEY + " "
                + "AND issuetype = Sub-task "
                + buildDataHubSprintClause(sprintNameOrId)
                + "AND assignee in " + DATAHUB_QA_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    public static String buildArcXpExecutionRateSubTasksJql(String sprintNameOrId) {
        return "project = " + ARCXP_PROJECT_KEY + " "
                + "AND issuetype in (Task, Sub-task) "
                + buildArcXpSprintClause(sprintNameOrId)
                + "AND \"QA by\" in " + ARCXP_USERS + " "
                + "ORDER BY lastViewed DESC";
    }

    private static String buildDataHubSprintClause(String sprintNameOrId) {
        String sanitizedSprint = sprintNameOrId == null ? "" : sprintNameOrId.trim();
        if (sanitizedSprint.matches("\\d+")) {
            return "AND sprint = " + sanitizedSprint + " ";
        }
        String escapedSprintName = sanitizedSprint.replace("\"", "\\\"");
        return "AND sprint = \"" + escapedSprintName + "\" ";
    }

    private static String buildArcXpSprintClause(String sprintNameOrId) {
        String sanitizedSprint = sprintNameOrId == null ? "" : sprintNameOrId.trim();
        if (sanitizedSprint.matches("\\d+")) {
            return "AND sprint = " + sanitizedSprint + " ";
        }
        String escapedSprintName = sanitizedSprint.replace("\"", "\\\"");
        return "AND sprint = \"" + escapedSprintName + "\" ";
    }
}

