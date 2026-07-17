package com.example.contracts;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(UserContract.BASE)
public interface UserContract {
    String BASE = "/api/contracts";

    @GetMapping("/{id}")
    UserResponse get(@PathVariable("contractId") Long id, @RequestParam(required = false) String filter);

    @GetMapping("/by-name")
    UserResponse find(String name);

    @GetMapping("/by-id")
    UserResponse find(Long id);

    @GetMapping("/override")
    UserResponse override(@RequestParam("contractName") String name);

    @PostMapping("/body")
    UserResponse create(@RequestBody(required = false) UserRequest request);
}
