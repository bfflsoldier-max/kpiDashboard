package com.qa.datafeeder;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(
        origins = "${dashboard.cors.allowed-origin:http://localhost:8080}",
        allowedHeaders = {"Authorization", "Content-Type"})
public class FeederProjectController {

    private final FeederProjectService service;
    private final AdminSessionService adminSessionService;

    public FeederProjectController(
            FeederProjectService service,
            AdminSessionService adminSessionService) {
        this.service = service;
        this.adminSessionService = adminSessionService;
    }

    @PostMapping
    public FeederProject saveProject(@Valid @RequestBody FeederProjectRequest request) {
        return service.save(request);
    }

    @GetMapping
    public List<FeederProject> getProjects() {
        return service.findAll();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DeleteProjectRequest request) {
        requireAdmin(authorization);
        if (!service.deleteProject(
                request.domain(),
                request.streamName(),
                request.projectId(),
                request.boardId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project registration not found");
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/stream")
    public ResponseEntity<Void> deleteStream(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody DeleteStreamRequest request) {
        requireAdmin(authorization);
        if (!service.deleteStream(request.domain(), request.streamName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stream not found");
        }
        return ResponseEntity.noContent().build();
    }

    private void requireAdmin(String authorization) {
        if (!adminSessionService.isValid(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
        }
    }
}
