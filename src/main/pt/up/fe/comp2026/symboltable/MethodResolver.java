package pt.up.fe.comp2026.symboltable;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MethodResolver {

    private final JmmSymbolTable table;

    public MethodResolver(JmmSymbolTable table) {
        this.table = table;
    }

    public Optional<JmmType> resolveMethodType(String className, String methodName, List<JmmType> argTypes) {
        return resolveMethodTypeInHierarchy(className, methodName, argTypes, new HashSet<>());
    }

    public Optional<SymbolTable> getClassSymbolTable(String className) {
        if (sameClass(className, table.getFullyQualifiedName())) {
            return Optional.of(table);
        }

        return table.getImportedSymbolTable(className);
    }

    public boolean isAssignable(JmmType sourceType, JmmType targetType) {
        if (sourceType.equals(targetType)) {
            return true;
        }

        if (!sourceType.isClass() || !targetType.isClass()) {
            return false;
        }

        var sourceClass = sourceType.asClass();
        var targetClass = targetType.asClass();

        if (sourceClass.staticRef() || targetClass.staticRef()) {
            return false;
        }

        return isSameClassOrSubclass(sourceClass.fullyQualifiedName(), targetClass.fullyQualifiedName());
    }

    private Optional<JmmType> resolveMethodTypeInHierarchy(String className, String methodName, List<JmmType> argTypes,
                                                           Set<String> visitedClasses) {
        if (!visitedClasses.add(className)) {
            return Optional.empty();
        }

        var classTable = getClassSymbolTable(className);
        if (classTable.isEmpty()) {
            return Optional.empty();
        }

        var methodType = resolveMethod(classTable.get(), methodName, argTypes)
                .map(MethodSymbol::returnType);

        if (methodType.isPresent()) {
            return methodType;
        }

        var superClassName = classTable.get().getSuperFullyQualifiedName();
        if (superClassName == null || sameClass(superClassName, className)) {
            return Optional.empty();
        }

        return resolveMethodTypeInHierarchy(superClassName, methodName, argTypes, visitedClasses);
    }

    private Optional<MethodSymbol> resolveMethod(SymbolTable symbolTable, String methodName, List<JmmType> argTypes) {
        var signature = Signature.of(methodName, argTypes);
        var exactMatch = symbolTable.getMethod(signature);
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        return symbolTable.getMethods(methodName).stream()
                .filter(method -> method.parameters().size() == argTypes.size())
                .filter(method -> parametersMatch(method, argTypes))
                .findFirst();
    }

    private boolean parametersMatch(MethodSymbol method, List<JmmType> argTypes) {
        for (int i = 0; i < argTypes.size(); i++) {
            var sourceType = argTypes.get(i);
            var targetType = method.parameters().get(i).type();

            if (!sourceType.equals(targetType) && !isAssignable(sourceType, targetType)) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameClassOrSubclass(String sourceClassName, String targetClassName) {
        if (sameClass(sourceClassName, targetClassName)) {
            return true;
        }

        var runtimeAssignable = isRuntimeAssignable(sourceClassName, targetClassName);
        if (runtimeAssignable.isPresent()) {
            return runtimeAssignable.get();
        }

        var sourceTable = getClassSymbolTable(sourceClassName);
        if (sourceTable.isEmpty()) {
            return false;
        }

        var superClassName = sourceTable.get().getSuperFullyQualifiedName();
        if (superClassName == null) {
            return false;
        }

        if (sameClass(superClassName, targetClassName)) {
            return true;
        }

        if (sameClass(superClassName, "Object") || sameClass(superClassName, "java.lang.Object")) {
            return false;
        }

        return isSameClassOrSubclass(superClassName, targetClassName);
    }

    private Optional<Boolean> isRuntimeAssignable(String sourceClassName, String targetClassName) {
        var sourceRuntimeClass = resolveRuntimeClass(sourceClassName);
        var targetRuntimeClass = resolveRuntimeClass(targetClassName);

        if (sourceRuntimeClass.isEmpty() || targetRuntimeClass.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(targetRuntimeClass.get().isAssignableFrom(sourceRuntimeClass.get()));
    }

    private Optional<Class<?>> resolveRuntimeClass(String className) {
        if (className == null || sameClass(className, table.getFullyQualifiedName())) {
            return Optional.empty();
        }

        var directClass = table.importer.tryClassOf(className);
        if (directClass.isPresent()) {
            return directClass;
        }

        var importedClass = table.getImportedFullyQualifiedName(className)
                .flatMap(table.importer::tryClassOf);
        if (importedClass.isPresent()) {
            return importedClass;
        }

        return table.importer.loadImplicit(className);
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
}
