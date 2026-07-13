package io.github.springapidiff.model;

import java.util.ArrayList;
import java.util.List;

public class ApiRequest {
    private List<ApiParameter> pathVariables = new ArrayList<ApiParameter>();
    private List<ApiParameter> queryParams = new ArrayList<ApiParameter>();
    private ApiBody body;

    public ApiRequest() {
    }

    public ApiRequest(List<ApiParameter> pathVariables, List<ApiParameter> queryParams, ApiBody body) {
        this.pathVariables = pathVariables;
        this.queryParams = queryParams;
        this.body = body;
    }

    public List<ApiParameter> getPathVariables() {
        return pathVariables;
    }

    public void setPathVariables(List<ApiParameter> pathVariables) {
        this.pathVariables = pathVariables;
    }

    public List<ApiParameter> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(List<ApiParameter> queryParams) {
        this.queryParams = queryParams;
    }

    public ApiBody getBody() {
        return body;
    }

    public void setBody(ApiBody body) {
        this.body = body;
    }

    public List<ApiParameter> pathVariables() {
        return pathVariables;
    }

    public List<ApiParameter> queryParams() {
        return queryParams;
    }

    public ApiBody body() {
        return body;
    }
}
