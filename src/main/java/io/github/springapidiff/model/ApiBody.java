package io.github.springapidiff.model;

import java.util.ArrayList;
import java.util.List;

public class ApiBody {
    private String type;
    private List<ApiField> fields = new ArrayList<ApiField>();

    public ApiBody() {
    }

    public ApiBody(String type, List<ApiField> fields) {
        this.type = type;
        this.fields = fields;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<ApiField> getFields() {
        return fields;
    }

    public void setFields(List<ApiField> fields) {
        this.fields = fields;
    }

    public String type() {
        return type;
    }

    public List<ApiField> fields() {
        return fields;
    }
}
