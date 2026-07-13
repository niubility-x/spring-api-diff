package io.github.springapidiff.util;

import com.github.javaparser.ast.type.Type;

public final class TypeNameNormalizer {
    private TypeNameNormalizer() {
    }

    public static String normalize(Type type) {
        return normalize(type.asString());
    }

    public static String normalize(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return "void";
        }
        String withoutAnnotations = typeName.replaceAll("@\\w+(\\([^)]*\\))?\\s*", "");
        return simplifyQualifiedNames(withoutAnnotations.replace("? extends ", "").replace("? super ", "").trim());
    }

    public static String dtoLookupName(String typeName) {
        String current = normalize(typeName);
        while (current.contains("<") && current.endsWith(">")) {
            String inner = firstGenericArgument(current);
            if (inner == null || inner.equals(current)) {
                break;
            }
            current = inner;
        }
        return rawSimpleName(current);
    }

    public static String rawSimpleName(String typeName) {
        String normalized = normalize(typeName);
        int genericStart = normalized.indexOf('<');
        if (genericStart >= 0) {
            normalized = normalized.substring(0, genericStart);
        }
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private static String firstGenericArgument(String typeName) {
        int start = typeName.indexOf('<');
        int end = typeName.lastIndexOf('>');
        if (start < 0 || end <= start) {
            return null;
        }
        String args = typeName.substring(start + 1, end);
        int depth = 0;
        for (int i = 0; i < args.length(); i++) {
            char ch = args.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                return normalize(args.substring(0, i));
            }
        }
        return normalize(args);
    }

    private static String simplifyQualifiedNames(String typeName) {
        StringBuilder result = new StringBuilder();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < typeName.length(); i++) {
            char ch = typeName.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '.') {
                token.append(ch);
            } else {
                appendSimpleToken(result, token);
                result.append(ch);
            }
        }
        appendSimpleToken(result, token);
        return result.toString().replaceAll("\\s+", "");
    }

    private static void appendSimpleToken(StringBuilder result, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        String value = token.toString();
        int dot = value.lastIndexOf('.');
        result.append(dot >= 0 ? value.substring(dot + 1) : value);
        token.setLength(0);
    }
}
