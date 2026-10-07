package com.qa.datafeeder;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "${dashboard.cors.allowed-origin:http://localhost:8080}")
public class FeederProjectController {

    private final FeederProjectService service;

    public FeederProjectController(FeederProjectService service) {
        this.service = service;
    }

    @PostMapping
    public FeederProject saveProject(@Valid @RequestBody FeederProjectRequest request) {
        return service.save(request);
    }

    @GetMapping
    public List<FeederProject> getProjects() {
        return service.findAll();
    }
}
