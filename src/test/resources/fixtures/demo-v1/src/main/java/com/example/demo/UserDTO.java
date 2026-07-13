package com.example.demo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class UserDTO {
    private Long id;

    @JsonProperty("user_name")
    private String name;

    private String email;

    @JsonIgnore
    private String internalToken;
}
