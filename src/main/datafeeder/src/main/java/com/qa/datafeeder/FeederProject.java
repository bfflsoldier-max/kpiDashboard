package com.qa.datafeeder;

public record FeederProject(
        String streamName,
        String domain,
        String projectName,
        String projectId,
        int boardId) {
}
