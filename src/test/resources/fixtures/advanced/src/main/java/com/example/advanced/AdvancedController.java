package com.example.advanced;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/advanced", "/internal/advanced"})
public class AdvancedController {
    @GetMapping({"/{id}", "/by-id/{id}"})
    public ResponseEntity<AdvancedResponse> get(@PathVariable Long id, @RequestParam(defaultValue = "active") String status) {
        return null;
    }

    @PostMapping
    public AdvancedResponse create(@RequestBody AdvancedRequest request) {
        return null;
    }

    @PutMapping("/{id}")
    public AdvancedResponse replace(@PathVariable Long id, @RequestBody(required = false) AdvancedRequest request) {
        return null;
    }

    @PatchMapping("/{id}")
    public AdvancedResponse patch(@PathVariable Long id, @RequestBody AdvancedRequest request) {
        return null;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
    }

    @RequestMapping(value = "/search", method = {RequestMethod.GET, RequestMethod.POST})
    public List<AdvancedResponse> search() {
        return null;
    }
}
