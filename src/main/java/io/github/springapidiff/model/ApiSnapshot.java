package io.github.springapidiff.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ApiSnapshot {
    private String version;
    private Instant generatedAt;
    private ProjectInfo project;
    private List<Endpoint> endpoints = new ArrayList<Endpoint>();

    public ApiSnapshot() {
    }

    public ApiSnapshot(String version, Instant generatedAt, ProjectInfo project, List<Endpoint> endpoints) {
        this.version = version;
        this.generatedAt = generatedAt;
        this.project = project;
        this.endpoints = endpoints;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public ProjectInfo getProject() {
        return project;
    }

    public void setProject(ProjectInfo project) {
        this.project = project;
    }

    public List<Endpoint> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    public String version() {
        return version;
    }

    public Instant generatedAt() {
        return generatedAt;
    }

    public ProjectInfo project() {
        return project;
    }

    public List<Endpoint> endpoints() {
        return endpoints;
    }
}
