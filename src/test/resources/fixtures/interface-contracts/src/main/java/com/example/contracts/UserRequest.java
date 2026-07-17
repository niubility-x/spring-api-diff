package com.example.contracts;

import javax.validation.constraints.NotBlank;

public class UserRequest {
    @NotBlank
    private String name;
}
