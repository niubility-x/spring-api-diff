package com.example.constants;

import static com.example.constants.ApiPaths.ADMIN;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ApiPaths.USERS, ADMIN})
public class ConstantController {
    private static final String DETAIL = "/{id}";
    private static final String SEARCH = "/search";

    @GetMapping(DETAIL)
    public String detail() {
        return null;
    }

    @RequestMapping(path = {SEARCH, "/lookup"}, method = {RequestMethod.GET, RequestMethod.POST})
    public String search() {
        return null;
    }

    @GetMapping(unknownPath())
    public String unresolved() {
        return null;
    }

    private static String unknownPath() {
        return "/unknown";
    }
}
