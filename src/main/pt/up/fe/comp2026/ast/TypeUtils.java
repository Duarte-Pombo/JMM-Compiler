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
import pt.up.fe.comp2026.symboltable.MethodResolver;
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
    private final MethodResolver methodResolver;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
        this.methodResolver = new MethodResolver(this.table);
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

    public static JmmArrayType stringArrayType() {
        return new JmmArrayType(new JmmClassType("String", false, false), 1);
    }

    public static JmmType convertType(JmmNode typeNode) {
        return convertType(typeNode, new ArrayList<>(), null);
    }

    public static JmmType convertType(JmmNode typeNode, List<String> imports, String currentClassFqName) {
        String kind = typeNode.getKind().toString();

        int arrayDimensions = getExplicitArrayDimensions(typeNode);

        JmmType baseType = switch (kind) {
            case "INT" -> JmmPrimitiveType.INT;
            case "BOOLEAN" -> JmmPrimitiveType.fromString("boolean").orElseThrow();
            case "VOID" -> JmmPrimitiveType.fromString("void").orElseThrow();
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
            default -> throw new UnsupportedOperationException("Unsupported type kind: " + kind);
        };

        return arrayDimensions > 0 ? new JmmArrayType(baseType, arrayDimensions) : baseType;
    }

    private static int getExplicitArrayDimensions(JmmNode arrayNode) {
        if (!arrayNode.getAttributes().contains("dims")) {
            return 0;
        }

        return arrayNode.getObjectAsList("dims", String.class).size();
    }

    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case PARENTHESES_EXPR -> getExprType(expr.getChild(0));
            case ARRAY_ACCESS -> getArrayAccessType(expr);
            case METHOD_CALL -> getMethodCallType(expr);
            case IMPLICIT_CALL -> getImplicitCallType(expr);
            case FIELD_ACCESS -> getFieldAccessType(expr);
            case NEGATION_EXPR, BOOLEAN_LITERAL -> booleanType();
            case UNARY_EXPR, INTEGER_LITERAL -> intType();
            case NEW_OBJECT -> getNewObjectType(expr);
            case NEW_ARRAY -> getNewArrayType(expr);
            case NEW_ARRAY_BY_EXTENSION -> getNewArrayByExtensionType(expr);
            case BINARY_EXPR -> getBinExprType(expr);
            case ARRAY -> getArrayType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr);
            case THIS -> getThisType(expr);
            default ->
                    throw new UnsupportedOperationException("Can't compute type for expression kind '" + expr.getKind() + "'");
        };
    }

    private JmmType getArrayType(JmmNode arrayExpr) {
        ARRAY.checkOrThrow(arrayExpr);

        if (arrayExpr.getChildren().isEmpty()) {
            return new JmmArrayType(intType(), 1);
        }

        var firstElementType = getExprType(arrayExpr.getChild(0));

        if (firstElementType.isArray()) {
            var nestedArrayType = (JmmArrayType) firstElementType;
            return new JmmArrayType(intType(), nestedArrayType.dimension() + 1);
        }

        return new JmmArrayType(intType(), 1);
    }

    private JmmType getArrayAccessType(JmmNode arrayAccessExpr) {
        ARRAY_ACCESS.checkOrThrow(arrayAccessExpr);

        var arrayType = getExprType(arrayAccessExpr.getChild(0));

        if (arrayType.isArray()) {
            var typedArray = (JmmArrayType) arrayType;
            int dims = typedArray.dimension();

            return dims > 1
                    ? new JmmArrayType(typedArray.itemType(), dims - 1)
                    : typedArray.itemType();
        }

        return intType();
    }

    private JmmType getNewArrayType(JmmNode newArrayExpr) {
        NEW_ARRAY.checkOrThrow(newArrayExpr);

        return new JmmArrayType(intType(), newArrayExpr.getChildren().size());
    }

    private JmmType getNewArrayByExtensionType(JmmNode newArrayByExtensionExpr) {
        NEW_ARRAY_BY_EXTENSION.checkOrThrow(newArrayByExtensionExpr);

        int explicitDimensions = getExplicitArrayDimensions(newArrayByExtensionExpr);
        int initializerDimensions = getArrayInitializerDimensions(newArrayByExtensionExpr.getChild(0));

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
            maxNestedDimensions = Math.max(maxNestedDimensions, getArrayInitializerDimensions(elem));
        }

        return 1 + maxNestedDimensions;
    }

    private JmmType getMethodCallType(JmmNode methodCallExpr) {
        METHOD_CALL.checkOrThrow(methodCallExpr);

        var methodName = methodCallExpr.get("name");
        var recvType = getExprType(methodCallExpr.getChild(0));
        var recvClass = recvType.asClass().fullyQualifiedName();
        var argTypes = methodCallExpr.getChildren().stream().skip(1).map(this::getExprType).toList();

        return methodResolver.resolveMethodType(recvClass, methodName, argTypes)
                .orElseThrow(() -> new RuntimeException("Method '" + Signature.of(methodName, argTypes) + "' not found in '" + recvClass + "'"));
    }

    private JmmType getImplicitCallType(JmmNode implicitCallExpr) {
        IMPLICIT_CALL.checkOrThrow(implicitCallExpr);

        var methodName = implicitCallExpr.get("name");
        var argTypes = implicitCallExpr.getChildren().stream().map(this::getExprType).toList();

        return methodResolver.resolveMethodType(table.getFullyQualifiedName(), methodName, argTypes)
                .orElseThrow(() -> new RuntimeException("Method not found: " + Signature.of(methodName, argTypes)));
    }

    private JmmType getFieldAccessType(JmmNode expr) {
        var fieldName = expr.get("name");
        var recvType = getExprType(expr.getChild(0));

        if ("length".equals(fieldName) && recvType.isArray()) {
            return intType();
        }

        if (!recvType.isClass()) {
            throw new RuntimeException("Access to a field not in a class" + recvType);
        }

        var recvClass = recvType.asClass().fullyQualifiedName();

        return methodResolver.getClassSymbolTable(recvClass)
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
        METHOD_DECL.check(methodDecl);

        var methodName = methodDecl.get("name");
        var params = new ArrayList<JmmType>();

        if (MAIN_METHOD_DECL.check(methodDecl)) {
            params.add(stringArrayType());
        } else {
            for (var paramNode : methodDecl.getChildren(JmmKind.PARAM)) {
                var paramTypeNode = paramNode.getChildren().getFirst();
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

        var typeOpt = getVariableType(varName, varRefExpr);
        if (typeOpt.isPresent()) {
            return typeOpt.get();
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
        return methodResolver.isAssignable(sourceType, targetType);
    }

    public Optional<JmmType> getVariableType(String varName, JmmNode scopeNode) {
        var methodDecl = scopeNode.getAncestor(METHOD_DECL);

        if (methodDecl.isPresent()) {
            var methodSignature = getMethodDeclSignature(methodDecl.get());
            var method = table.getMethod(methodSignature);

            if (method.isPresent()) {
                var localVar = method.get().getLocalVariable(varName).map(Symbol::type);
                if (localVar.isPresent()) return localVar;

                var param = method.get().getParameter(varName).map(Symbol::type);
                if (param.isPresent()) return param;
            }
        }

        return table.getField(varName).map(Symbol::type);
    }

}
