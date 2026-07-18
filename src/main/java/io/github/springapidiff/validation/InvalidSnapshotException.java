package io.github.springapidiff.validation;

public class InvalidSnapshotException extends RuntimeException {
    public InvalidSnapshotException(String message) {
        super(message);
    }
}
