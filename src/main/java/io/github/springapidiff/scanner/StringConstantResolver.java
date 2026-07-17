package io.github.springapidiff.scanner;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.EnclosedExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class StringConstantResolver {
    private final Map<ConstantKey, ConstantDefinition> constants = new LinkedHashMap<>();
    private final Map<ClassOrInterfaceDeclaration, ResolutionContext> contexts = new IdentityHashMap<>();
    private final Map<ConstantKey, Optional<String>> cache = new HashMap<>();

    StringConstantResolver(List<CompilationUnit> units) {
        for (CompilationUnit unit : units) {
            for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                ResolutionContext context = context(unit, type);
                contexts.put(type, context);
                indexConstants(type, context);
            }
        }
    }

    Optional<String> resolve(Expression expression, ClassOrInterfaceDeclaration owner) {
        ResolutionContext context = contexts.get(owner);
        return context == null ? Optional.empty() : resolve(expression, context, new HashSet<>());
    }

    private void indexConstants(ClassOrInterfaceDeclaration type, ResolutionContext context) {
        for (FieldDeclaration field : type.getFields()) {
            boolean constantField = type.isInterface()
                || (field.hasModifier(Modifier.Keyword.STATIC) && field.hasModifier(Modifier.Keyword.FINAL));
            if (!constantField || !isString(field)) {
                continue;
            }
            for (VariableDeclarator variable : field.getVariables()) {
                if (variable.getInitializer().isPresent()) {
                    ConstantKey key = new ConstantKey(context.owner(), variable.getNameAsString());
                    ConstantDefinition definition = new ConstantDefinition(variable.getInitializer().get(), context);
                    if (constants.containsKey(key)) {
                        constants.put(key, ConstantDefinition.ambiguousDefinition());
                    } else {
                        constants.put(key, definition);
                    }
                }
            }
        }
    }

    private boolean isString(FieldDeclaration field) {
        String type = field.getElementType().asString();
        return "String".equals(type) || "java.lang.String".equals(type);
    }

    private Optional<String> resolve(Expression expression, ResolutionContext context, Set<ConstantKey> resolving) {
        if (expression instanceof StringLiteralExpr) {
            return Optional.of(((StringLiteralExpr) expression).asString());
        }
        if (expression instanceof EnclosedExpr) {
            return resolve(((EnclosedExpr) expression).getInner(), context, resolving);
        }
        if (expression instanceof BinaryExpr) {
            BinaryExpr binary = (BinaryExpr) expression;
            if (binary.getOperator() != BinaryExpr.Operator.PLUS) {
                return Optional.empty();
            }
            Optional<String> left = resolve(binary.getLeft(), context, resolving);
            Optional<String> right = resolve(binary.getRight(), context, resolving);
            return left.isPresent() && right.isPresent()
                ? Optional.of(left.get() + right.get())
                : Optional.empty();
        }
        if (expression instanceof NameExpr) {
            return resolveName(((NameExpr) expression).getNameAsString(), context, resolving);
        }
        if (expression instanceof FieldAccessExpr) {
            return resolveFieldAccess((FieldAccessExpr) expression, context, resolving);
        }
        return Optional.empty();
    }

    private Optional<String> resolveName(String name, ResolutionContext context, Set<ConstantKey> resolving) {
        for (String owner : context.lexicalOwners()) {
            ConstantKey key = new ConstantKey(owner, name);
            if (constants.containsKey(key)) {
                return resolveConstant(key, resolving);
            }
        }
        ConstantKey imported = context.staticImports().get(name);
        return imported == null ? Optional.empty() : resolveConstant(imported, resolving);
    }

    private Optional<String> resolveFieldAccess(
        FieldAccessExpr fieldAccess,
        ResolutionContext context,
        Set<ConstantKey> resolving) {
        Optional<String> qualifier = qualifier(fieldAccess.getScope());
        if (!qualifier.isPresent()) {
            return Optional.empty();
        }
        String field = fieldAccess.getNameAsString();
        for (String owner : ownerCandidates(qualifier.get(), context)) {
            ConstantKey key = new ConstantKey(owner, field);
            if (constants.containsKey(key)) {
                return resolveConstant(key, resolving);
            }
        }
        return Optional.empty();
    }

    private Optional<String> qualifier(Expression expression) {
        if (expression instanceof NameExpr) {
            return Optional.of(((NameExpr) expression).getNameAsString());
        }
        if (expression instanceof FieldAccessExpr) {
            FieldAccessExpr fieldAccess = (FieldAccessExpr) expression;
            Optional<String> scope = qualifier(fieldAccess.getScope());
            return scope.map(value -> value + "." + fieldAccess.getNameAsString());
        }
        return Optional.empty();
    }

    private List<String> ownerCandidates(String qualifier, ResolutionContext context) {
        List<String> candidates = new ArrayList<>();
        candidates.add(qualifier);
        String imported = context.typeImports().get(qualifier);
        if (imported != null) {
            candidates.add(imported);
        }
        for (String lexicalOwner : context.lexicalOwners()) {
            candidates.add(lexicalOwner + "." + qualifier);
        }
        if (!context.packageName().isEmpty()) {
            candidates.add(context.packageName() + "." + qualifier);
        }
        return candidates;
    }

    private Optional<String> resolveConstant(ConstantKey key, Set<ConstantKey> resolving) {
        Optional<String> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        ConstantDefinition definition = constants.get(key);
        if (definition == null || definition.ambiguous() || !resolving.add(key)) {
            return Optional.empty();
        }
        Optional<String> result = resolve(definition.initializer(), definition.context(), resolving);
        resolving.remove(key);
        cache.put(key, result);
        return result;
    }

    private ResolutionContext context(CompilationUnit unit, ClassOrInterfaceDeclaration type) {
        String packageName = unit.getPackageDeclaration()
            .map(declaration -> declaration.getNameAsString())
            .orElse("");
        List<String> typeNames = new ArrayList<>();
        Node current = type;
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration) {
                typeNames.add(0, ((ClassOrInterfaceDeclaration) current).getNameAsString());
            }
            current = current.getParentNode().orElse(null);
        }
        String owner = join(packageName, String.join(".", typeNames));
        List<String> lexicalOwners = new ArrayList<>();
        for (int size = typeNames.size(); size > 0; size--) {
            lexicalOwners.add(join(packageName, String.join(".", typeNames.subList(0, size))));
        }
        Map<String, String> typeImports = new HashMap<>();
        Map<String, ConstantKey> staticImports = new HashMap<>();
        for (ImportDeclaration declaration : unit.getImports()) {
            if (declaration.isAsterisk()) {
                continue;
            }
            String importedName = declaration.getNameAsString();
            int separator = importedName.lastIndexOf('.');
            if (separator < 0) {
                continue;
            }
            String simpleName = importedName.substring(separator + 1);
            if (declaration.isStatic()) {
                staticImports.put(simpleName, new ConstantKey(importedName.substring(0, separator), simpleName));
            } else {
                typeImports.put(simpleName, importedName);
            }
        }
        return new ResolutionContext(packageName, owner, lexicalOwners, typeImports, staticImports);
    }

    private String join(String packageName, String typeName) {
        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }

    private static class ConstantKey {
        private final String owner;
        private final String field;

        private ConstantKey(String owner, String field) {
            this.owner = owner;
            this.field = field;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConstantKey)) {
                return false;
            }
            ConstantKey key = (ConstantKey) other;
            return owner.equals(key.owner) && field.equals(key.field);
        }

        @Override
        public int hashCode() {
            return 31 * owner.hashCode() + field.hashCode();
        }
    }

    private static class ConstantDefinition {
        private final Expression initializer;
        private final ResolutionContext context;
        private final boolean ambiguous;

        private ConstantDefinition(Expression initializer, ResolutionContext context) {
            this(initializer, context, false);
        }

        private ConstantDefinition(Expression initializer, ResolutionContext context, boolean ambiguous) {
            this.initializer = initializer;
            this.context = context;
            this.ambiguous = ambiguous;
        }

        private static ConstantDefinition ambiguousDefinition() {
            return new ConstantDefinition(null, null, true);
        }

        private Expression initializer() {
            return initializer;
        }

        private ResolutionContext context() {
            return context;
        }

        private boolean ambiguous() {
            return ambiguous;
        }
    }

    private static class ResolutionContext {
        private final String packageName;
        private final String owner;
        private final List<String> lexicalOwners;
        private final Map<String, String> typeImports;
        private final Map<String, ConstantKey> staticImports;

        private ResolutionContext(
            String packageName,
            String owner,
            List<String> lexicalOwners,
            Map<String, String> typeImports,
            Map<String, ConstantKey> staticImports) {
            this.packageName = packageName;
            this.owner = owner;
            this.lexicalOwners = lexicalOwners;
            this.typeImports = typeImports;
            this.staticImports = staticImports;
        }

        private String packageName() {
            return packageName;
        }

        private String owner() {
            return owner;
        }

        private List<String> lexicalOwners() {
            return lexicalOwners;
        }

        private Map<String, String> typeImports() {
            return typeImports;
        }

        private Map<String, ConstantKey> staticImports() {
            return staticImports;
        }
    }
}
