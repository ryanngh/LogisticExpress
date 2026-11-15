package com.ryan.logisticexpress.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Username or email cannot be blank")
        @JsonAlias({"username", "email"})
        String usernameOrEmail,

        @NotBlank(message = "Password cannot be blank")
        String password
) {}
