package io.github.springapidiff.cli;

interface ProgressListener {
    void onProgress(int step, int total, String message);
}
