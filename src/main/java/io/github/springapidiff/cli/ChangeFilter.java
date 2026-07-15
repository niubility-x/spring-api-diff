package io.github.springapidiff.cli;

import io.github.springapidiff.diff.Change;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

class ChangeFilter {
    private final List<String> ignoredEndpoints;

    ChangeFilter(List<String> ignoredEndpoints) {
        this.ignoredEndpoints = ignoredEndpoints == null ? Collections.emptyList() : ignoredEndpoints;
    }

    List<Change> filter(List<Change> changes) {
        if (ignoredEndpoints.isEmpty()) {
            return changes;
        }
        List<Change> filtered = new ArrayList<>();
        for (Change change : changes) {
            if (!ignored(change)) {
                filtered.add(change);
            }
        }
        return filtered;
    }

    private boolean ignored(Change change) {
        String endpoint = change.endpoint();
        if (endpoint == null) {
            return false;
        }
        for (String pattern : ignoredEndpoints) {
            if (Pattern.matches(globToRegex(pattern), endpoint)) {
                return true;
            }
        }
        return false;
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                boolean doubleStar = i + 1 < glob.length() && glob.charAt(i + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    i++;
                }
            } else {
                if ("\\.[]{}()+-^$?|".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        return regex.toString();
    }
}
