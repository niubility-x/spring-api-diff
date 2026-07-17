package com.example.contracts;

import org.springframework.web.bind.annotation.RestController;

@RestController
public class SharedController implements SharedA, SharedB {
    @Override
    public String same(String value) {
        return null;
    }

    @Override
    public String mappingConflict(String value) {
        return null;
    }

    @Override
    public String parameterConflict(String value) {
        return null;
    }
}
