package com.example.constants;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(missingPath())
public class UnresolvedController {
    @GetMapping("/leaked")
    public String leaked() {
        return null;
    }

    private static String missingPath() {
        return "/missing";
    }
}
