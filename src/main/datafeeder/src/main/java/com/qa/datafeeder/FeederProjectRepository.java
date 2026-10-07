package com.qa.datafeeder;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class FeederProjectRepository {

    private static final org.springframework.jdbc.core.RowMapper<FeederProject> PROJECT_ROW_MAPPER =
            (resultSet, rowNumber) -> new FeederProject(
                    resultSet.getString("stream_name"),
                    resultSet.getString("domain_name"),
                    resultSet.getString("project_name"),
                    resultSet.getString("project_id"),
                    resultSet.getInt("board_id"));

    private final JdbcTemplate jdbcTemplate;

    public FeederProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public FeederProject save(FeederProject project) {
        List<Long> existingIds = jdbcTemplate.query(
                "SELECT id FROM feeder_projects "
                        + "WHERE domain_name = ? AND stream_name = ? AND project_id = ? AND board_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"),
                project.domain(),
                project.streamName(),
                project.projectId(),
                project.boardId());

        if (existingIds.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT INTO feeder_projects "
                            + "(stream_name, domain_name, project_name, project_id, board_id) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    project.streamName(),
                    project.domain(),
                    project.projectName(),
                    project.projectId(),
                    project.boardId());
        } else {
            jdbcTemplate.update(
                    "UPDATE feeder_projects SET project_name = ? WHERE id = ?",
                    project.projectName(),
                    existingIds.get(0));
        }
        return project;
    }

    public List<FeederProject> findAll() {
        return jdbcTemplate.query(
                "SELECT stream_name, domain_name, project_name, project_id, board_id "
                        + "FROM feeder_projects ORDER BY domain_name, stream_name, project_name",
                PROJECT_ROW_MAPPER);
    }
}
