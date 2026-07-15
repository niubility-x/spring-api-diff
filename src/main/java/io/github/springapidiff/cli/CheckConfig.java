package io.github.springapidiff.cli;

import java.util.ArrayList;
import java.util.List;

class CheckConfig {
    private String base;
    private String head;
    private Boolean worktree;
    private String module;
    private Modules modules = new Modules();
    private String report;
    private Boolean failOnBreaking;
    private Boolean fetch;
    private Ignore ignore = new Ignore();
    private List<String> include = new ArrayList<>();
    private List<String> exclude = new ArrayList<>();

    String base() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    String head() {
        return head;
    }

    public void setHead(String head) {
        this.head = head;
    }

    Boolean worktree() {
        return worktree;
    }

    public void setWorktree(Boolean worktree) {
        this.worktree = worktree;
    }

    String module() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    Modules modules() {
        return modules == null ? new Modules() : modules;
    }

    public void setModules(Modules modules) {
        this.modules = modules;
    }

    String report() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    Boolean failOnBreaking() {
        return failOnBreaking;
    }

    public void setFailOnBreaking(Boolean failOnBreaking) {
        this.failOnBreaking = failOnBreaking;
    }

    Boolean fetch() {
        return fetch;
    }

    public void setFetch(Boolean fetch) {
        this.fetch = fetch;
    }

    Ignore ignore() {
        return ignore == null ? new Ignore() : ignore;
    }

    public void setIgnore(Ignore ignore) {
        this.ignore = ignore;
    }

    List<String> include() {
        return include == null ? new ArrayList<>() : include;
    }

    public void setInclude(List<String> include) {
        this.include = include;
    }

    List<String> exclude() {
        return exclude == null ? new ArrayList<>() : exclude;
    }

    public void setExclude(List<String> exclude) {
        this.exclude = exclude;
    }

    static class Modules {
        private List<String> include = new ArrayList<>();
        private List<String> exclude = new ArrayList<>();

        List<String> include() {
            return include == null ? new ArrayList<>() : include;
        }

        public void setInclude(List<String> include) {
            this.include = include;
        }

        List<String> exclude() {
            return exclude == null ? new ArrayList<>() : exclude;
        }

        public void setExclude(List<String> exclude) {
            this.exclude = exclude;
        }
    }

    static class Ignore {
        private List<String> endpoints = new ArrayList<>();

        List<String> endpoints() {
            return endpoints == null ? new ArrayList<>() : endpoints;
        }

        public void setEndpoints(List<String> endpoints) {
            this.endpoints = endpoints;
        }
    }
}
