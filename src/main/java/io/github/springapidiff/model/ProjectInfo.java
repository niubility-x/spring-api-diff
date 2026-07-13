package io.github.springapidiff.model;

public class ProjectInfo {
    private String name;
    private String javaVersion;
    private String springBootVersion;

    public ProjectInfo() {
    }

    public ProjectInfo(String name, String javaVersion, String springBootVersion) {
        this.name = name;
        this.javaVersion = javaVersion;
        this.springBootVersion = springBootVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    public String getSpringBootVersion() {
        return springBootVersion;
    }

    public void setSpringBootVersion(String springBootVersion) {
        this.springBootVersion = springBootVersion;
    }

    public String name() {
        return name;
    }

    public String javaVersion() {
        return javaVersion;
    }

    public String springBootVersion() {
        return springBootVersion;
    }
}
