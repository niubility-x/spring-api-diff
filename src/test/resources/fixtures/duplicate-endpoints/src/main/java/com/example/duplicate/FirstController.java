package com.example.duplicate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FirstController {
    @GetMapping("/duplicate")
    public String first() {
        return null;
    }
}
