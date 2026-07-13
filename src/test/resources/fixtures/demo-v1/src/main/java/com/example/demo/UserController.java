package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    @GetMapping("/{id}")
    public UserDTO getUser(@PathVariable Long id, @RequestParam(required = false) String status) {
        return null;
    }

    @PostMapping
    public UserDTO createUser(@RequestBody CreateUserRequest request) {
        return null;
    }
}
