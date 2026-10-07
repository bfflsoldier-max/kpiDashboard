# Jira Dashboard

A configurable Jira dashboard. Domain and project definitions are provided through Spring configuration; no company, project, user, or KPI data is checked in by default.

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

An empty `/api/domains` response means no domain/project definitions are configured. Jira URL and credentials are required for Jira discovery calls.

## Data Feeder

The Data Feeder is a standalone Spring Boot service in `src/main/datafeeder`. It owns its form, API, H2 configuration, schema, and persisted project registrations. Start it in a separate terminal:

```sh
cd src/main/datafeeder
../../../mvnw spring-boot:run
```

Open `http://localhost:8090/` to register a stream, domain, project name, Jira project ID, and positive board ID. The service saves registrations in its local `data/` directory. The dashboard remains a separate service; run it from the repository root with `./mvnw spring-boot:run` and open `http://localhost:8080/`. Its menu fetches feeder projects from `http://localhost:8090/api/projects`. Configure a different feeder URL in `src/main/resources/static/js/config.js` if the services are deployed separately. The dashboard also redirects `/datafeeder` to the standalone feeder; configure that destination with `DATAFEEDER_URL`. Set `DASHBOARD_ORIGIN` on the feeder service to the dashboard origin when it is not one of `http://localhost:8080` or `http://localhost:8081`.

The project menu groups registrations by domain and stream. Selecting a stream shows a tile for each registered project with a sprint label in `ProjectID_BoardID` format. Project names containing `Datahub` display as `Data Hub`. Existing sample projects remain available.

## Run

Set `JIRA_URL` and `JIRA_PAT`, provide the domain/project configuration, then run:

```sh
sh mvnw spring-boot:run
```
