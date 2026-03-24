package pt.up.fe.comp2026.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.analysis.AnalysisVisitorWithTable;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NewObjectArguments extends AnalysisVisitorWithTable {

    public NewObjectArguments(SymbolTable table) {
        super(table);
    }

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.NEW_OBJECT, this::visitNewObject);
    }

    private Void visitNewObject(JmmNode newObjectExpr, SymbolTable ignored) {
        var className = newObjectExpr.get("name");
        var argTypes = getArgumentTypes(newObjectExpr);

        if (argTypes.isEmpty() && !newObjectExpr.getChildren().isEmpty()) {
            return null;
        }

        if (isCurrentClass(className)) {
            if (!argTypes.isEmpty()) {
                addReport(newError(newObjectExpr,
                        "Constructor '" + formatConstructorSignature(className, argTypes)
                                + "' not found in current class"));
            }
            return null;
        }

        var runtimeClass = resolveRuntimeClass(className);
        if (runtimeClass.isEmpty()) {
            return null;
        }

        if (!hasMatchingConstructor(runtimeClass.get(), argTypes)) {
            addReport(newError(newObjectExpr,
                    "Constructor '" + formatConstructorSignature(runtimeClass.get().getSimpleName(), argTypes)
                            + "' not found in '" + runtimeClass.get().getName() + "'"));
        }

        return null;
    }

    private List<JmmType> getArgumentTypes(JmmNode newObjectExpr) {
        var argTypes = new ArrayList<JmmType>();

        for (var arg : newObjectExpr.getChildren()) {
            try {
                argTypes.add(types.getExprType(arg));
            } catch (RuntimeException e) {
                addReport(newError(newObjectExpr, "Invalid constructor argument: " + e.getMessage()));
                return List.of();
            }
        }

        return argTypes;
    }

    private boolean hasMatchingConstructor(Class<?> runtimeClass, List<JmmType> argTypes) {
        for (var constructor : runtimeClass.getConstructors()) {
            if (parametersMatch(constructor, argTypes)) {
                return true;
            }
        }

        return false;
    }

    private boolean parametersMatch(Constructor<?> constructor, List<JmmType> argTypes) {
        var parameterTypes = constructor.getParameterTypes();

        if (parameterTypes.length != argTypes.size()) {
            return false;
        }

        for (int i = 0; i < parameterTypes.length; i++) {
            var targetType = convertReflectedType(parameterTypes[i]);
            if (targetType.isEmpty()) {
                return false;
            }

            var sourceType = argTypes.get(i);
            if (!sourceType.equals(targetType.get()) && !types.isAssignable(sourceType, targetType.get())) {
                return false;
            }
        }

        return true;
    }

    private Optional<JmmType> convertReflectedType(Class<?> type) {
        int dimensions = 0;
        while (type.isArray()) {
            dimensions++;
            type = type.getComponentType();
        }

        Optional<JmmType> baseType;
        if (type.isPrimitive()) {
            baseType = JmmPrimitiveType.fromString(type.getName()).map(JmmType.class::cast);
        } else {
            baseType = Optional.of(JmmClassType.ofInstance(type.getName(), true));
        }

        if (baseType.isEmpty()) {
            return Optional.empty();
        }

        if (dimensions == 0) {
            return baseType;
        }

        return Optional.of(new JmmArrayType(baseType.get(), dimensions));
    }

    private Optional<Class<?>> resolveRuntimeClass(String className) {
        var importedFqName = table.getImportedFullyQualifiedName(className);
        if (importedFqName.isPresent()) {
            return tryLoadClass(importedFqName.get());
        }

        return table.getImplicitImport(className)
                .flatMap(symbolTable -> tryLoadClass(symbolTable.getFullyQualifiedName()));
    }

    private Optional<Class<?>> tryLoadClass(String fullyQualifiedName) {
        try {
            return Optional.of(Class.forName(fullyQualifiedName));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    private boolean isCurrentClass(String className) {
        return sameClass(className, table.getClassName()) || sameClass(className, table.getFullyQualifiedName());
    }

    private boolean sameClass(String left, String right) {
        if (left == null || right == null) {
            return false;
        }

        return left.equals(right) || simpleName(left).equals(simpleName(right));
    }

    private String simpleName(String className) {
        var lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    private String formatConstructorSignature(String className, List<JmmType> argTypes) {
        var argList = argTypes.stream().map(JmmType::print).toList();
        return className + "(" + String.join(", ", argList) + ")";
    }
}
