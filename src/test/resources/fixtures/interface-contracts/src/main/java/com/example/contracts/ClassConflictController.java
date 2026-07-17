package com.example.contracts;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClassConflictController implements LeftContract, RightContract {
    @GetMapping("/leaked")
    public String leaked() {
        return null;
    }
}
