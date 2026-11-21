package com.ryan.logisticexpress.hub.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AssignWardsRequest(
        @NotEmpty(message = "At least one ward code must be provided")
        List<String> wardCodes
) {}
