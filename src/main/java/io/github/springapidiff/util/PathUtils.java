package io.github.springapidiff.util;

public final class PathUtils {
    private PathUtils() {
    }

    public static String join(String left, String right) {
        String first = clean(left);
        String second = clean(right);
        if (first.equals("/")) {
            return second;
        }
        if (second.equals("/")) {
            return first;
        }
        return normalize(first + "/" + second);
    }

    public static String normalize(String path) {
        String cleaned = clean(path);
        return cleaned.replaceAll("/{2,}", "/");
    }

    private static String clean(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        String cleaned = path.trim();
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }
        while (cleaned.length() > 1 && cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }
}
