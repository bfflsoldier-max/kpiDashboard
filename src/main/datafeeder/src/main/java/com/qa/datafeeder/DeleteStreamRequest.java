package com.qa.datafeeder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteStreamRequest(
        @NotBlank @Size(max = 100) String domain,
        @NotBlank @Size(max = 100) String streamName) {
}
