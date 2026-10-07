# Data Feeder

This is a standalone Spring Boot service. Its API, H2 database, schema, and static form are self-contained so this folder can be moved into a separate repository.

## Run

From this directory in the dashboard repository:

```sh
../../../mvnw spring-boot:run
```

In a standalone checkout, use `mvn spring-boot:run`. The service listens on port `8090` by default and serves the form at `/`; the dashboard's `/datafeeder` route redirects here. H2 stores project registrations under `./data/` relative to the service working directory. Override the port with `PORT`, and set `DASHBOARD_ORIGIN` to the browser origin hosting the dashboard or feeder form, such as `http://127.0.0.1:5500` when using VS Code Live Server. Multiple allowed origins may be supplied as a comma-separated value.

The form posts to `http://localhost:8090/api/projects` by default, even when served by Live Server. Change the `datafeeder-api-base-url` meta tag in `src/main/resources/static/index.html` if the API is hosted elsewhere.

## API

- `GET /api/projects` returns saved projects.
- `POST /api/projects` creates or updates a project using `streamName`, `domain`, `projectName`, `projectId`, and a positive `boardId`.

Registrations are upserted by domain, stream, project ID, and board ID. The dashboard groups them under their domain and stream, and labels project tiles as `ProjectID_BoardID`.
