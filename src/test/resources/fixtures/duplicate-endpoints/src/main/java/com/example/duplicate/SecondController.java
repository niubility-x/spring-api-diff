package com.example.duplicate;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SecondController {
    @GetMapping("/duplicate")
    public String second() {
        return null;
    }
}
