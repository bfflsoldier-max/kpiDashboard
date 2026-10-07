package com.qa.datafeeder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DeleteProjectRequest(
        @NotBlank @Size(max = 100) String domain,
        @NotBlank @Size(max = 100) String streamName,
        @NotBlank @Size(max = 100) String projectId,
        @Positive int boardId) {
}
