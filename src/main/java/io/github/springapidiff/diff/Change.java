package io.github.springapidiff.diff;

public class Change {
    private Severity severity;
    private ChangeType type;
    private String endpoint;
    private String path;
    private String oldValue;
    private String newValue;
    private String message;

    public Change() {
    }

    public Change(Severity severity, ChangeType type, String endpoint, String path, String oldValue, String newValue, String message) {
        this.severity = severity;
        this.type = type;
        this.endpoint = endpoint;
        this.path = path;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public ChangeType getType() {
        return type;
    }

    public void setType(ChangeType type) {
        this.type = type;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Severity severity() {
        return severity;
    }

    public ChangeType type() {
        return type;
    }

    public String endpoint() {
        return endpoint;
    }

    public String path() {
        return path;
    }

    public String oldValue() {
        return oldValue;
    }

    public String newValue() {
        return newValue;
    }

    public String message() {
        return message;
    }
}
