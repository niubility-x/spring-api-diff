package io.github.springapidiff.cli;

class BaseSelection {
    private final String ref;
    private final String source;

    BaseSelection(String ref, String source) {
        this.ref = ref;
        this.source = source;
    }

    String ref() {
        return ref;
    }

    String source() {
        return source;
    }
}
