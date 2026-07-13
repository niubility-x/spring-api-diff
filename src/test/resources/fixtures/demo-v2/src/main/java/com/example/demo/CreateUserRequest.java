package com.example.demo;

import jakarta.validation.constraints.NotBlank;

public class CreateUserRequest {
    private String name;
    private Integer email;

    @NotBlank
    private String phone;
}
