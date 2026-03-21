package pt.up.fe.comp2026.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;


/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static TypeUtils with(SymbolTable table) {
        return new TypeUtils(table);
    }

    public static JmmPrimitiveType intType() {
        return JmmPrimitiveType.INT;
    }

    public static JmmPrimitiveType booleanType() {
        return JmmPrimitiveType.BOOLEAN;
    }

    // Used by Opt
    public static JmmType convertType(JmmNode typeNode) {
        return convertType(typeNode, new ArrayList<>(), null);
    }

    public static JmmType convertType(JmmNode typeNode, List<String> imports, String currentClassFqName) {
        String kind = typeNode.getKind().toString();
        int arrayDimensions = getArrayDimensions(typeNode);

        return switch (kind) {
            // Primitives
            case "INT" -> JmmPrimitiveType.INT;
            case "BOOLEAN" -> JmmPrimitiveType.fromString("boolean").orElseThrow();
            case "VOID" -> JmmPrimitiveType.fromString("void").orElseThrow();

            // Custom Classes
            case "ID" -> {
                String typeName = typeNode.get("val");
                Optional<String> importFq = imports.stream().filter(i -> i.endsWith("." + typeName) || i.equals(typeName)).findFirst();

                if (importFq.isPresent()) {
                    yield new JmmClassType(importFq.get(), true, false);
                } else if (currentClassFqName != null && currentClassFqName.endsWith("." + typeName)) {
                    yield new JmmClassType(currentClassFqName, false, false);
                } else if (currentClassFqName != null && currentClassFqName.equals(typeName)) {
                    yield new JmmClassType(currentClassFqName, false, false);
                } else {
                    yield new JmmClassType(typeName, false, false);
                }
            }

            // Arrays
            case "INTEGER_ARRAY" -> new JmmArrayType(JmmPrimitiveType.INT, arrayDimensions);
            case "STRING_ARRAY" -> new JmmArrayType(new JmmClassType("String", false, false), arrayDimensions);
            case "ID_ARRAY" -> {
                String typeName = typeNode.get("val");
                Optional<String> importFq = imports.stream().filter(i -> i.endsWith("." + typeName) || i.equals(typeName)).findFirst();

                if (importFq.isPresent()) {
                    yield new JmmArrayType(new JmmClassType(importFq.get(), true, false), arrayDimensions);
                } else if (currentClassFqName != null && currentClassFqName.endsWith("." + typeName)) {
                    yield new JmmArrayType(new JmmClassType(currentClassFqName, false, false), arrayDimensions);
                } else if (currentClassFqName != null && currentClassFqName.equals(typeName)) {
                    yield new JmmArrayType(new JmmClassType(currentClassFqName, false, false), arrayDimensions);
                } else {
                    yield new JmmArrayType(new JmmClassType(typeName, false, false), arrayDimensions);
                }
            }

            default -> throw new UnsupportedOperationException("Unsupported type kind: " + kind);
        };
    }

    private static int getArrayDimensions(JmmNode arrayNode) {
        int explicitDimensions = getExplicitArrayDimensions(arrayNode);
        return explicitDimensions > 0 ? explicitDimensions : 1;
    }

    private static int getExplicitArrayDimensions(JmmNode arrayNode) {
        if (!arrayNode.getAttributes().contains("dims")) {
            return 0;
        }

        return arrayNode.getObjectAsList("dims", String.class).size();
    }

    public JmmType getStmtType(JmmNode stmt) {
        STMT.checkOrThrow(stmt);
        var voidType = JmmPrimitiveType.fromString("void").orElseThrow();

        return switch (stmt.getKind()) {
            case COMPOUND_STMT, IF_ELSE_STMT, WHILE_STMT, DO_WHILE_STMT, FOR_STMT, ASSIGN_STMT, ARRAY_ASSIGN_STMT ->
                    voidType;
            case EXPR_STMT -> getExprType(stmt.getChild(0));
            case RETURN_STMT -> stmt.getChildren().isEmpty() ? voidType : getExprType(stmt.getChild(0));
            default ->
                    throw new UnsupportedOperationException("Can't compute type for statement kind '" + stmt.getKind() + "'");
        };
    }

    /**
     * Gets the {@link JmmType} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case PARENTHESES_EXPR -> getExprType(expr.getChild(0));
            case ARRAY_ACCESS -> getArrayAccessType(expr);
            case METHOD_CALL -> getMethodCallType(expr);
            case IMPLICIT_CALL -> getImplicitCallType(expr);
            case FIELD_ACCESS -> getFieldAccessType(expr);
            case NEGATION_EXPR -> booleanType();
            case UNARY_EXPR -> intType();
            case NEW_OBJECT -> getNewObjectType(expr);
            case NEW_ARRAY -> getNewArrayType(expr);
            case NEW_ARRAY_BY_EXTENSION -> getNewArrayByExtensionType(expr);
            case BINARY_EXPR -> getBinExprType(expr);
            case ARRAY -> getArrayType(expr);
            case INTEGER_LITERAL -> intType();
            case BOOLEAN_LITERAL -> booleanType();
            case VAR_REF_EXPR -> getVarExprType(expr);
            case THIS -> getThisType(expr);
            default ->
                    throw new UnsupportedOperationException("Can't compute type for expression kind '" + expr.getKind() + "'");
        };
    }

    private JmmType getArrayType(JmmNode arrayExpr) {
        ARRAY.checkOrThrow(arrayExpr);

        if (arrayExpr.getChildren().isEmpty()) {
            throw new RuntimeException("Cannot infer type for empty array literal");
        }

        var firstElementType = getExprType(arrayExpr.getChild(0));

        for (var elementExpr : arrayExpr.getChildren()) {
            var elementType = getExprType(elementExpr);
            if (!elementType.equals(firstElementType)) {
                throw new RuntimeException("Array literal elements must have the same type");
            }
        }

        if (firstElementType.isArray()) {
            var nestedArrayType = (JmmArrayType) firstElementType;
            return new JmmArrayType(nestedArrayType.itemType(), nestedArrayType.dimension() + 1);
        }

        return new JmmArrayType(firstElementType, 1);
    }

    private JmmType getArrayAccessType(JmmNode arrayAccessExpr) {
        ARRAY_ACCESS.checkOrThrow(arrayAccessExpr);

        var arrayType = getExprType(arrayAccessExpr.getChild(0));
        var indexType = getExprType(arrayAccessExpr.getChild(1));

        if (!arrayType.isArray()) {
            throw new RuntimeException("Array access target is not an array: " + arrayType);
        }

        if (!indexType.equals(intType())) {
            throw new RuntimeException("Array index must be int, got: " + indexType);
        }

        var typedArray = (JmmArrayType) arrayType;
        int dims = typedArray.dimension();

        return dims > 1
                ? new JmmArrayType(typedArray.itemType(), dims - 1)
                : typedArray.itemType();
    }

    private JmmType getNewArrayType(JmmNode newArrayExpr) {
        NEW_ARRAY.checkOrThrow(newArrayExpr);

        for (var sizeExpr : newArrayExpr.getChildren()) {
            var sizeType = getExprType(sizeExpr);
            if (!sizeType.equals(intType())) {
                throw new RuntimeException("Array size must be int, got: " + sizeType);
            }
        }

        return new JmmArrayType(intType(), newArrayExpr.getChildren().size());
    }

    private JmmType getNewArrayByExtensionType(JmmNode newArrayByExtensionExpr) {
        NEW_ARRAY_BY_EXTENSION.checkOrThrow(newArrayByExtensionExpr);

        int explicitDimensions = getExplicitArrayDimensions(newArrayByExtensionExpr);

        int initializerDimensions = 0;
        if (!newArrayByExtensionExpr.getChildren().isEmpty()) {
            initializerDimensions = getArrayInitializerDimensions(newArrayByExtensionExpr.getChild(0));
        }

        int dimensions = explicitDimensions > 0 ? explicitDimensions : initializerDimensions;
        if (dimensions <= 0) {
            dimensions = 1;
        }

        return new JmmArrayType(intType(), dimensions);
    }

    private int getArrayInitializerDimensions(JmmNode node) {
        if (ARRAY_ELEM.check(node)) {
            if (node.getChildren().isEmpty()) {
                return 0;
            }

            return getArrayInitializerDimensions(node.getChild(0));
        }

        if (!ARRAY_INIT.check(node)) {
            return 0;
        }

        int maxNestedDimensions = 0;
        for (var elem : node.getChildren(ARRAY_ELEM)) {
            if (elem.getChildren().isEmpty()) {
                continue;
            }

            maxNestedDimensions = Math.max(maxNestedDimensions, getArrayInitializerDimensions(elem.getChild(0)));
        }

        return 1 + maxNestedDimensions;
    }

    private JmmType getMethodCallType(JmmNode methodCallExpr) {
        METHOD_CALL.checkOrThrow(methodCallExpr);

        var methodName = methodCallExpr.get("name");
        var recvType = getExprType(methodCallExpr.getChild(0));

        if (!recvType.isClass()) {
            throw new RuntimeException("Method call receiver is not a class type: " + recvType);
        }

        var recvClass = recvType.asClass().fullyQualifiedName();
        var argTypes = methodCallExpr.getChildren().stream().skip(1).map(this::getExprType).toList();

        return resolveMethodTypeInHierarchy(recvClass, methodName, argTypes)
                .orElseThrow(() -> new RuntimeException("Method '" + Signature.of(methodName, argTypes) + "' not found in '" + recvClass + "'"));
    }

    private JmmType getImplicitCallType(JmmNode implicitCallExpr) {
        IMPLICIT_CALL.checkOrThrow(implicitCallExpr);

        var methodName = implicitCallExpr.get("name");
        var argTypes = implicitCallExpr.getChildren().stream().map(this::getExprType).toList();

        return resolveMethodTypeInHierarchy(table.getFullyQualifiedName(), methodName, argTypes)
                .orElseThrow(() -> new RuntimeException("Method not found: " + Signature.of(methodName, argTypes)));
    }

    private JmmType getFieldAccessType(JmmNode expr) {
        var fieldName = expr.get("name");
        var recvType = getExprType(expr.getChild(0));

        if ("length".equals(fieldName)) {
            if (!recvType.isArray()) {
                throw new RuntimeException("Field " + fieldName + " is not an array");
            }
            return intType();
        }

        if (!recvType.isClass()) {
            throw new RuntimeException("Access to a field not in a class" + recvType);
        }

        var recvClass = recvType.asClass().fullyQualifiedName();

        return getClassSymbolTable(recvClass)
                .flatMap(st -> st.getField(fieldName))
                .map(Symbol::type)
                .orElseThrow(() -> new RuntimeException("Field '" + fieldName + "' not in '" + recvClass + "'"));
    }

    private JmmType getNewObjectType(JmmNode newObjectExpr) {
        var className = newObjectExpr.get("name");
        var importedFqName = table.getImportedFullyQualifiedName(className);

        if (importedFqName.isPresent()) {
            return new JmmClassType(importedFqName.get(), true, false);
        }

        if (className.equals(table.getClassName()) || className.equals(table.getFullyQualifiedName())) {
            return new JmmClassType(table.getFullyQualifiedName(), false, false);
        }

        return new JmmClassType(className, false, false);
    }

    private JmmType getThisType(JmmNode thisExpr) {
        THIS.checkOrThrow(thisExpr);
        return new JmmClassType(table.getFullyQualifiedName(), false, false);
    }

    public Signature getMethodDeclSignature(JmmNode methodDecl) {
        // Ensure given node is a MethodDecl
        METHOD_DECL.check(methodDecl);

        // Get name of the method
        var methodName = methodDecl.get("name");
        var params = new ArrayList<JmmType>();

        if (methodDecl.getKind().toString().equals("MAIN_METHOD_DECL")) {
            params.add(new JmmArrayType(new JmmClassType("String", false, false), 1));
        } else {
            for (var paramNode : methodDecl.getChildren(JmmKind.PARAM)) {
                var paramTypeNode = paramNode.getChildren().getFirst();
                // We can use the 1-parameter convertType overload here safely
                params.add(convertType(paramTypeNode));
            }
        }

        return new Signature(methodName, params);
    }


    private JmmType getBinExprType(JmmNode binaryExpr) {

        // Get operator
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "-", "*", "/", "%" -> intType();
            case "&&", "||", "<=", ">=", "==", "!=", "<", ">" -> booleanType();
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private JmmType getVarExprType(JmmNode varRefExpr) {
        VAR_REF_EXPR.checkOrThrow(varRefExpr);

        var varName = varRefExpr.get("name");

        var methodDecl = varRefExpr.getAncestor(METHOD_DECL)
                .orElseThrow(() -> new RuntimeException("VarRef '" + varName + "' outside a method scope"));

        var methodSignature = getMethodDeclSignature(methodDecl);
        var method = table.getMethod(methodSignature)
                .orElseThrow(() -> new RuntimeException("Could not resolve method for signature " + methodSignature));

        var localVarType = method.getLocalVariable(varName).map(Symbol::type);
        if (localVarType.isPresent()) {
            return localVarType.get();
        }

        var paramType = method.getParameter(varName).map(Symbol::type);
        if (paramType.isPresent()) {
            return paramType.get();
        }

        var fieldType = table.getField(varName).map(Symbol::type);
        if (fieldType.isPresent()) {
            return fieldType.get();
        }

        if (varName.equals(table.getClassName()) || varName.equals(table.getFullyQualifiedName())) {
            return new JmmClassType(table.getFullyQualifiedName(), false, true);
        }

        var importedFqName = table.getImportedFullyQualifiedName(varName);
        if (importedFqName.isPresent()) {
            return new JmmClassType(importedFqName.get(), true, true);
        }

        var implicitImport = table.getImplicitImport(varName);
        if (implicitImport.isPresent()) {
            return new JmmClassType(implicitImport.get().getFullyQualifiedName(), true, true);
        }

        throw new RuntimeException("Variable '" + varName + "' is not defined in current scope");
    }

    public Optional<JmmType> getExpectedType(JmmNode expr, JmmNode currentMethod) {
        var parent = expr.getParent();
        if (parent == null) {
            return Optional.empty();
        }

        if (parent.isInstance(ASSIGN_STMT)) {
            return expr.getIndexOfSelf() == 1
                    ? Optional.of(getExprType(parent.getChild(0)))
                    : Optional.empty();
        }

        if (parent.isInstance(RETURN_STMT)) {
            return expr.getIndexOfSelf() == 0 && currentMethod != null
                    ? Optional.of(getMethodReturnType(currentMethod))
                    : Optional.empty();
        }

        if (parent.isInstance(VAR_DECL)) {
            return expr.getIndexOfSelf() == 1
                    ? Optional.of(getDeclaredType(parent))
                    : Optional.empty();
        }

        return Optional.empty();
    }

    public JmmType getDeclaredType(JmmNode declarationNode) {
        return convertType(declarationNode.getChild(0), table.getImports(), table.getFullyQualifiedName());
    }

    public JmmType getMethodReturnType(JmmNode methodDecl) {
        var signature = getMethodDeclSignature(methodDecl);
        return table.getMethod(signature)
                .map(MethodSymbol::returnType)
                .orElseThrow(() -> new RuntimeException("Could not resolve method for signature " + signature));
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

    private Optional<SymbolTable> getClassSymbolTable(String className) {
        if (sameClass(className, table.getFullyQualifiedName())) {
            return Optional.of(table);
        }

        return table.getImportedSymbolTable(className);
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

    private Optional<JmmType> resolveMethodTypeInHierarchy(String className, String methodName, List<JmmType> argTypes) {
        return resolveMethodTypeInHierarchy(className, methodName, argTypes, new HashSet<>());
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

        return resolveReflectedMethodType(className, methodName, argTypes);
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

    private Optional<JmmType> resolveReflectedMethodType(String className, String methodName, List<JmmType> argTypes) {
        try {
            var javaClass = resolveJavaClass(className);

            for (var method : javaClass.getMethods()) {
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
        } catch (ClassNotFoundException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private boolean reflectionParametersMatch(Method method, List<JmmType> argTypes) {
        var parameterTypes = method.getParameterTypes();

        for (int i = 0; i < parameterTypes.length; i++) {
            var expected = parameterTypes[i];
            var actual = argTypes.get(i);

            try {
                var actualClass = toJavaClass(actual);

                if (!wrap(expected).isAssignableFrom(wrap(actualClass))) {
                    return false;
                }
            } catch (ClassNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    private Class<?> resolveJavaClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return Class.forName("java.lang." + className);
        }
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
            int[] dimensions = new int[arrayType.dimension()];
            return Array.newInstance(componentClass, dimensions).getClass();
        }

        if (type.isClass()) {
            return resolveJavaClass(type.asClass().fullyQualifiedName());
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

}
