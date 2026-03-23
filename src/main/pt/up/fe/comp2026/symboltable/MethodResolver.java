package pt.up.fe.comp2026.symboltable;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
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

    private Optional<JmmType> resolveMethodTypeInHierarchy(String className, String methodName, List<JmmType> argTypes, Set<String> visitedClasses) {
        if (!visitedClasses.add(className)) {
            return Optional.empty();
        }

        var classTable = getClassSymbolTable(className);
        if (classTable.isPresent()) {
            var methodType = resolveMethod(classTable.get(), methodName, argTypes)
                    .map(MethodSymbol::returnType);

            if (methodType.isPresent()) {
                return methodType;
            }

            var superClassName = classTable.get().getSuperFullyQualifiedName();
            if (superClassName != null && !sameClass(superClassName, className)) {
                var inheritedMethodType = resolveMethodTypeInHierarchy(superClassName, methodName, argTypes, visitedClasses);
                if (inheritedMethodType.isPresent()) {
                    return inheritedMethodType;
                }
            }
        }

        var javaClass = resolveClass(className);
        if (javaClass.isEmpty()) {
            return Optional.empty();
        }

        for (var method : javaClass.get().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }

            if (method.getParameterCount() != argTypes.size()) {
                continue;
            }

            if (reflectionParametersMatch(method, argTypes)) {
                return Optional.of(fromJavaType(method.getReturnType()));
            }
        }

        return Optional.empty();
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

    private Optional<Class<?>> resolveClass(String className) {
        var javaClass = table.importer.tryClassOf(className);
        if (javaClass.isPresent()) {
            return javaClass;
        }

        var importedFqName = table.getImportedFullyQualifiedName(className);
        if (importedFqName.isPresent()) {
            javaClass = table.importer.tryClassOf(importedFqName.get());
            if (javaClass.isPresent()) {
                return javaClass;
            }
        }

        return table.importer.loadImplicit(className);
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

    private boolean reflectionParametersMatch(Method method, List<JmmType> argTypes) {
        var parameterTypes = method.getParameterTypes();

        for (int i = 0; i < parameterTypes.length; i++) {
            try {
                var expectedType = wrap(parameterTypes[i]);
                var actualType = wrap(toJavaClass(argTypes.get(i)));

                if (!expectedType.isAssignableFrom(actualType)) {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    private boolean isSameClassOrSubclass(String sourceClassName, String targetClassName) {
        if (sameClass(sourceClassName, targetClassName)) {
            return true;
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

    private Class<?> toJavaClass(JmmType type) throws ClassNotFoundException {
        if (type.equals(JmmPrimitiveType.INT)) {
            return int.class;
        }

        if (type.equals(JmmPrimitiveType.BOOLEAN)) {
            return boolean.class;
        }

        if (type.equals(JmmPrimitiveType.VOID)) {
            return void.class;
        }

        if (type.isArray()) {
            var arrayType = (JmmArrayType) type;
            var componentClass = toJavaClass(arrayType.itemType());
            return Array.newInstance(componentClass, new int[arrayType.dimension()]).getClass();
        }

        if (type.isClass()) {
            var javaClass = resolveClass(type.asClass().fullyQualifiedName());
            if (javaClass.isPresent()) {
                return javaClass.get();
            }
        }

        throw new ClassNotFoundException("Unsupported type: " + type.print());
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }

        if (type == int.class) {
            return Integer.class;
        }

        if (type == boolean.class) {
            return Boolean.class;
        }

        if (type == long.class) {
            return Long.class;
        }

        if (type == double.class) {
            return Double.class;
        }

        if (type == float.class) {
            return Float.class;
        }

        if (type == char.class) {
            return Character.class;
        }

        if (type == byte.class) {
            return Byte.class;
        }

        if (type == short.class) {
            return Short.class;
        }

        return Void.class;
    }

    private JmmType fromJavaType(Class<?> javaType) {
        if (javaType == void.class) {
            return JmmPrimitiveType.VOID;
        }

        if (javaType == int.class) {
            return JmmPrimitiveType.INT;
        }

        if (javaType == boolean.class) {
            return JmmPrimitiveType.BOOLEAN;
        }

        if (javaType.isArray()) {
            int dims = 0;
            var componentType = javaType;
            while (componentType.isArray()) {
                dims++;
                componentType = componentType.getComponentType();
            }

            return new JmmArrayType(fromJavaType(componentType), dims);
        }

        var fqName = javaType.getName();
        if (fqName.startsWith("java.lang.")) {
            return new JmmClassType(javaType.getSimpleName(), false, false);
        }

        return new JmmClassType(fqName, true, false);
    }
}
