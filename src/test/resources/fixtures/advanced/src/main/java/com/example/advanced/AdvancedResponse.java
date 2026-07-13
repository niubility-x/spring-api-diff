package com.example.advanced;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AdvancedResponse extends BaseDTO {
    private Long id;

    @JsonProperty("display_name")
    private String name;
}
