package com.qa.datafeeder;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FeederProjectService {

    private final FeederProjectRepository repository;

    public FeederProjectService(FeederProjectRepository repository) {
        this.repository = repository;
    }

    public FeederProject save(FeederProjectRequest request) {
        return repository.save(new FeederProject(
                request.streamName().trim(),
                request.domain().trim(),
                request.projectName().trim(),
                request.projectId().trim(),
                request.boardId()));
    }

    public List<FeederProject> findAll() {
        return repository.findAll();
    }
}
