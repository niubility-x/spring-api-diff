package io.github.springapidiff.model;

public class Endpoint {
    private String id;
    private String method;
    private String path;
    private String controller;
    private String handler;
    private ApiRequest request;
    private ApiResponse response;

    public Endpoint() {
    }

    public Endpoint(String id, String method, String path, String controller, String handler, ApiRequest request, ApiResponse response) {
        this.id = id;
        this.method = method;
        this.path = path;
        this.controller = controller;
        this.handler = handler;
        this.request = request;
        this.response = response;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getController() {
        return controller;
    }

    public void setController(String controller) {
        this.controller = controller;
    }

    public String getHandler() {
        return handler;
    }

    public void setHandler(String handler) {
        this.handler = handler;
    }

    public ApiRequest getRequest() {
        return request;
    }

    public void setRequest(ApiRequest request) {
        this.request = request;
    }

    public ApiResponse getResponse() {
        return response;
    }

    public void setResponse(ApiResponse response) {
        this.response = response;
    }

    public String id() {
        return id;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String controller() {
        return controller;
    }

    public String handler() {
        return handler;
    }

    public ApiRequest request() {
        return request;
    }

    public ApiResponse response() {
        return response;
    }
}
