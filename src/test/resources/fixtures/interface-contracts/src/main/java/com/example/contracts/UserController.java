package com.example.contracts;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController implements UserContract {
    @Override
    public UserResponse get(Long userId, String query) {
        return null;
    }

    @Override
    public UserResponse find(String name) {
        return null;
    }

    @Override
    public UserResponse find(Long id) {
        return null;
    }

    @Override
    public UserResponse create(UserRequest request) {
        return null;
    }
    @Override
    @PostMapping("/custom")
    public UserResponse override(@RequestParam(value = "implementationName", required = false) String name) {
        return null;
    }
}
