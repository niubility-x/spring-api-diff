package io.github.springapidiff.model;

public class ApiField {
    private String name;
    private String type;
    private boolean required;

    public ApiField() {
    }

    public ApiField(String name, String type, boolean required) {
        this.name = name;
        this.type = type;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public boolean required() {
        return required;
    }
}
