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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        var signature = Signature.of(methodName, argTypes);

        var ownerTable = recvClass.equals(table.getFullyQualifiedName())
                ? Optional.of(table)
                : table.getImportedSymbolTable(recvClass);

        return ownerTable
                .flatMap(st -> st.getMethod(signature))
                .map(MethodSymbol::returnType)
                .orElseThrow(() -> new RuntimeException("Method '" + signature + "' not found in '" + recvClass + "'"));
    }

    private JmmType getImplicitCallType(JmmNode implicitCallExpr) {
        IMPLICIT_CALL.checkOrThrow(implicitCallExpr);

        var methodName = implicitCallExpr.get("name");
        var argTypes = implicitCallExpr.getChildren().stream().map(this::getExprType).toList();
        var signature = Signature.of(methodName, argTypes);

        return table.getMethod(signature)
                    .map(MethodSymbol::returnType)
                    .orElseThrow(() -> new RuntimeException("Method not found: " + signature));
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

        var ownerTable = recvClass.equals(table.getFullyQualifiedName())
                ? Optional.of(table)
                : table.getImportedSymbolTable(recvClass);

        return ownerTable.flatMap(st -> st.getField(fieldName))
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

}
