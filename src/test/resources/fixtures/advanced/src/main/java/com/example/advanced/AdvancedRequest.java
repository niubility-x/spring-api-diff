package com.example.advanced;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AdvancedRequest {
    @NotBlank
    private String name;

    @JsonProperty(required = true)
    private String email;

    @NotNull
    private Integer age;

    private String nickname;
}
