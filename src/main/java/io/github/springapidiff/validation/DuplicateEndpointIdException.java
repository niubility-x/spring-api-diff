package io.github.springapidiff.validation;

public class DuplicateEndpointIdException extends RuntimeException {
    public DuplicateEndpointIdException(String message) {
        super(message);
    }
}
