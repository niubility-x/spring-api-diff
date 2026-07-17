package com.example.contracts;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/shared")
public interface SharedB {
    @GetMapping("/same")
    String same(@RequestParam("value") String value);

    @GetMapping("/mapping-conflict-b")
    String mappingConflict(String value);

    @GetMapping("/parameter-conflict")
    String parameterConflict(@RequestParam("right") String value);
}
