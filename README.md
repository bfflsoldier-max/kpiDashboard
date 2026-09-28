# Jira Dashboard

A configurable Jira dashboard backend. Domain and project definitions are provided through Spring configuration; no company, project, user, or KPI data is checked in by default.

## Configuration

Provide Jira connection settings through environment variables or an untracked `config/local.properties` file:

```properties
jira.url=https://jira.example.com
jira.token=${JIRA_PAT}
dashboard.domains[0].id=engineering
dashboard.domains[0].name=Engineering
dashboard.domains[0].projects[0].id=project-one
dashboard.domains[0].projects[0].name=Example Project
dashboard.domains[0].projects[0].jira-project-id=12345
```

`board-id` can be supplied for a project when a Jira project has multiple boards; when omitted, the backend discovers the board with the latest active sprint. Keep credentials and organization-specific configuration out of version control.

## API

- `GET /api/domains` lists configured domains.
- `GET /api/domains/{domainId}/projects` lists projects for a domain.
- `GET /api/projects/{projectId}/boards` discovers Jira boards for a configured project.
- `GET /api/projects/{projectId}/sprints?state=active,closed` lists sprints for the selected board.

An empty `/api/domains` response means the Data Feeder or deployment configuration has not supplied domain/project definitions yet. Jira URL and credentials are required only for Jira discovery calls.

## Run

Set `JIRA_URL` and `JIRA_PAT`, provide the domain/project configuration, then run:

```sh
sh mvnw spring-boot:run
```
