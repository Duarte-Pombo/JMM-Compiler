package pt.up.fe.comp2026.ast;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

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
            case "INTEGER_ARRAY" -> new JmmArrayType(JmmPrimitiveType.INT, 1);
            case "STRING_ARRAY" -> new JmmArrayType(new JmmClassType("String", false, false), 1);
            case "ID_ARRAY" -> {
                String typeName = typeNode.get("val");
                Optional<String> importFq = imports.stream().filter(i -> i.endsWith("." + typeName) || i.equals(typeName)).findFirst();

                if (importFq.isPresent()) {
                    yield new JmmArrayType(new JmmClassType(importFq.get(), true, false), 1);
                } else if (currentClassFqName != null && currentClassFqName.endsWith("." + typeName)) {
                    yield new JmmArrayType(new JmmClassType(currentClassFqName, false, false), 1);
                } else if (currentClassFqName != null && currentClassFqName.equals(typeName)) {
                    yield new JmmArrayType(new JmmClassType(currentClassFqName, false, false), 1);
                } else {
                    yield new JmmArrayType(new JmmClassType(typeName, false, false), 1);
                }
            }

            default -> throw new UnsupportedOperationException("Unsupported type kind: " + kind);
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
            // case ARRAY_ACCESS ->
            // case METHOD_CALL -> getMethodCallType(expr);
            // case IMPLICIT_CALL ->
            // case FIELD_ACCESS ->
            case NEGATION_EXPR -> booleanType();
            case UNARY_EXPR -> intType();
            // case NEW_OBJECT -> getNewObjectType(expr);
            case NEW_ARRAY -> intType();
            case NEW_ARRAY_BY_EXTENSION -> intType();
            case BINARY_EXPR -> getBinExprType(expr);
            // case ARRAY ->
            case INTEGER_LITERAL -> intType();
            case BOOLEAN_LITERAL -> booleanType();
            case VAR_REF_EXPR -> getVarExprType(expr);
            // case THIS -> getThisType(expr);
            default ->
                    throw new UnsupportedOperationException("Can't compute type for expression kind '" + expr.getKind() + "'");
        };
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
        System.out.println("[TODO] TypeUtils.getVarExprType(): Implement type inference for VarExpr. You will need to determine in which method the VarRef is and use the symbol table");
        return intType();
    }

}
