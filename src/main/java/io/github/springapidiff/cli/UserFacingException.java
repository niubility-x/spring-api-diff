package io.github.springapidiff.cli;

import java.io.IOException;

class UserFacingException extends IOException {
    UserFacingException(String message) {
        super(message);
    }

    UserFacingException(String message, Throwable cause) {
        super(message, cause);
    }
}
